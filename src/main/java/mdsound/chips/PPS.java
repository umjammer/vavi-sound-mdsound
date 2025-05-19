package mdsound.chips;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import vavi.util.ByteUtil;


/**
 * PPS.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2022-07-08 nsano initial version <br>
 */
public class PPS {

    private static class Header {
        private int address;
        private int length;
        private int toneOfs;
        private int volumeOfs;
    }

    private double samplingRate = 44100.0;
    private static final int MAX_PPS = 14;
    private byte[] ppsDt = null;
    private Header[] ppsHd = null;
    // Single note mode?
    private boolean singleFlag;
    // Reproduce at half the frequency?
    private boolean lowCpuCheckFlag;
    // during Keyon ?
    private boolean keyonFlag;
    private int dataOffset1;
    private int dataOffset2;
    // Current position (decimal)
    private int dataXor1;
    // Current position (decimal)
    private int dataXor2;
    private int tick1;
    private int tick2;
    private int tickXor1;
    private int tickXor2;
    private int dataSize1;
    private int dataSize2;
    private int volume1;
    private int volume2;
    private int keyoffVol;
    private int[] emitTable = new int[16];
    private boolean interpolation = true;
    private boolean real = false;
    private BiConsumer<Integer, Integer> psg = null;
    private static final int[] table = {
            0, 0, 0, 5, 9, 10, 11, 12, 13, 13, 14, 14, 14, 15, 15, 15,
            0, 0, 3, 5, 9, 10, 11, 12, 13, 13, 14, 14, 14, 15, 15, 15,
            0, 3, 5, 7, 9, 10, 11, 12, 13, 13, 14, 14, 14, 15, 15, 15,
            5, 5, 7, 9, 10, 11, 12, 13, 13, 13, 14, 14, 14, 15, 15, 15,
            9, 9, 9, 10, 11, 12, 12, 13, 13, 14, 14, 14, 15, 15, 15, 15,
            10, 10, 10, 11, 12, 12, 13, 13, 13, 14, 14, 14, 15, 15, 15, 15,
            11, 11, 11, 12, 12, 13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15,
            12, 12, 12, 12, 13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15, 15,
            13, 13, 13, 13, 13, 13, 14, 14, 14, 14, 14, 15, 15, 15, 15, 15,
            13, 13, 13, 13, 14, 14, 14, 14, 14, 14, 15, 15, 15, 15, 15, 15,
            14, 14, 14, 14, 14, 14, 14, 14, 14, 15, 15, 15, 15, 15, 15, 15,
            14, 14, 14, 14, 14, 14, 14, 15, 15, 15, 15, 15, 15, 15, 15, 15,
            14, 14, 14, 14, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
            15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
            15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15,
            15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15
    };

    /**
     * Volume Settings
     */
    public void setVolume(int vol) {
        // psg.SetVolume(vol);

        double _base = 0x4000 * 2 / 3.0 * Math.pow(10.0, vol / 40.0);
        for (int i = 15; i >= 1; i--) {
            emitTable[i] = (int) _base;
            _base /= 1.189207115;
        }
        emitTable[0] = 0;
    }

    public void play(int al, int bh, int bl) {
        int num = al;
        int shift = bh;
        //logger.log(Level.TRACE, bh);
        int volshift = bl;

        if (ppsHd[num].address < 0) return;

        int a = 225 + ppsHd[num].toneOfs;
        a %= 256;
        a += shift;
        a = Math.min(Math.max(a, 1), 255);

        if (ppsHd[num].volumeOfs + volshift >= 15) return;
        // Do not play when volume is below 0

        if (!singleFlag && keyonFlag) {
            // Double Polyphony Processing
            volume2 = volume1; // Move 1st note to 2nd note
            dataOffset2 = dataOffset1;
            dataSize2 = dataSize1;
            dataXor2 = dataXor1;
            tick2 = tick1;
            tickXor2 = tickXor1;
        } else {
            // Play on first note
            dataSize2 = -1; // The second sound is stopped
        }

        volume1 = ppsHd[num].volumeOfs + volshift;
        dataOffset1 = ppsHd[num].address;
        dataSize1 = ppsHd[num].length; // Mute the first sound and play
        dataXor1 = 0;
        if (lowCpuCheckFlag) {
            tick1 = (int) (((int) (8000 * a / 225.) << 16) / samplingRate);
            tickXor1 = tick1 & 0xffff;
            tick1 >>= 16;
        } else {
            tick1 = (int) (((int) (16000 * a / 225.) << 16) / samplingRate);
            tickXor1 = tick1 & 0xffff;
            tick1 >>= 16;
        }

        // psg.SetReg(0x07, psg.GetReg(0x07) | 0x24); // Tone/Noise C off
        keyonFlag = true; // Start of play
    }

    public void stop() {
        keyonFlag = false;
        dataOffset1 = dataOffset2 = -1;
        dataSize1 = dataSize2 = -1;
    }

    public boolean setParam(int paramno, int data) {
        switch (paramno & 1) {
        case 0:
            singleFlag = data != 0;
            return true;
        case 1:
            lowCpuCheckFlag = data != 0;
            return true;
        default:
            return false;
        }
    }

    private void int04() {
        // TODO: Not implemented
    }

    public void update(int[][] outputs, int samples) {
        int i, al1, al2, ah1, ah2;
        int data = 0;

        if (!keyonFlag && keyoffVol == 0) {
            return;
        }

        for (i = 0; i < samples; i++) {

            if (dataSize1 > 1) {
                al1 = ppsDt[dataOffset1] - volume1;
                al2 = ppsDt[dataOffset1 + 1] - volume1;
                if (al1 < 0) al1 = 0;
                if (al2 < 0) al2 = 0;
            } else {
                al1 = al2 = 0;
            }

            if (dataSize2 > 1) {
                ah1 = ppsDt[dataOffset2] - volume2;
                ah2 = ppsDt[dataOffset2 + 1] - volume2;
                if (ah1 < 0) ah1 = 0;
                if (ah2 < 0) ah2 = 0;
            } else {
                ah1 = ah2 = 0;
            }

            if (real) {
                al1 = table[(al1 << 4) + ah1];
                psg.accept(0x0a, al1);
            } else {
                if (interpolation) {
                    data = (emitTable[al1] * (0x10000 - dataXor1) + emitTable[al2] * dataXor1 +
                            emitTable[ah1] * (0x10000 - dataXor2) + emitTable[ah2] * dataXor2) / 0x10000;

                } else {
                    data = emitTable[al1] + emitTable[ah1];
                }
            }

            keyoffVol = (keyoffVol * 255) / 258;

            if (!real) {
                if (!keyonFlag) data += keyoffVol;
                //if (keyoff_vol != 0) logger.log(Level.DEBUG, "keyoff_vol%d".formatted(keyoff_vol));
                outputs[0][i] = Math.max(Math.min(outputs[0][i] + data, Short.MAX_VALUE), Short.MIN_VALUE);
                outputs[1][i] = Math.max(Math.min(outputs[1][i] + data, Short.MAX_VALUE), Short.MIN_VALUE);
            }

            //  psg.mix(dest, 1);
            //  dest += 2;

            if (dataSize2 > 1) { // Double Polyphony synthesis playback
                dataXor2 += tickXor2;
                if (dataXor2 >= 0x1_0000) {
                    dataSize2--;
                    dataOffset2++;
                    dataXor2 -= 0x1_0000;
                }
                dataSize2 -= tick2;
                dataOffset2 += tick2;

                if (lowCpuCheckFlag) {
                    dataXor2 += tickXor2;
                    if (dataXor2 >= 0x1_0000) {
                        dataSize2--;
                        dataOffset2++;
                        dataXor2 -= 0x1_0000;
                    }
                    dataSize2 -= tick2;
                    dataOffset2 += tick2;
                }
            }

            dataXor1 += tickXor1;
            if (dataXor1 >= 0x1_0000) {
                dataSize1--;
                dataOffset1++;
                dataXor1 -= 0x1_0000;
            }
            dataSize1 -= tick1;
            dataOffset1 += tick1;

            if (lowCpuCheckFlag) {
                dataXor1 += tickXor1;
                if (dataXor1 >= 0x1_0000) {
                    dataSize1--;
                    dataOffset1++;
                    dataXor1 -= 0x1_0000;
                }
                dataSize1 -= tick1;
                dataOffset1 += tick1;
            }

            if (dataSize1 <= 1 && dataSize2 <= 1) { // Both stopped
                if (keyonFlag) {
                    int ad = dataSize1 - 1;
                    if (ad >= 0 && ad < ppsDt.length)
                        keyoffVol += emitTable[ppsDt[ad]] / 8;
                }
                keyonFlag = false; // Stop sound
                if (real) {
                    psg.accept(0x0a, 0); // Set Volume to 0
                }
            } else if (dataSize1 <= 1 && dataSize2 > 1) { // Only the second note is stopped
                volume1 = volume2;
                dataSize1 = dataSize2;
                dataOffset1 = dataOffset2;
                dataXor1 = dataXor2;
                tick1 = tick2;
                tickXor1 = tickXor2;
                dataSize2 = -1;

                int ad = dataSize1 - 1;
                if (ad >= 0 && ad < ppsDt.length)
                    keyoffVol += emitTable[ppsDt[ad]] / 8;

            } else if (dataSize1 > 1 && dataSize2 < 1) { // Only the second note is stopped
                if (dataOffset2 != -1) {
                    int ad = dataSize2 - 1;
                    if (ad >= 0 && ad < ppsDt.length)
                        keyoffVol += emitTable[ppsDt[ad]] / 8;
                    dataOffset2 = -1;
                }
            }
        }
    }

    public int load(byte[] pcmData) {
        if (pcmData == null || pcmData.length <= MAX_PPS * 6) return -1;

        List<Byte> o = new ArrayList<>();

        // Read into temporary buffer
        for (int i = MAX_PPS * 6; i < pcmData.length; i++) {
            o.add((byte) ((pcmData[i] >> 4) & 0xf));
            o.add((byte) ((pcmData[i] >> 0) & 0xf));
        }

        // Creating Data
        // PPS correction (to combat small noise) / Attenuate with 160 samples
        for (int i = 0; i < MAX_PPS; i++) {
            int address = (pcmData[i * 6 + 0] & 0xff) + (pcmData[i * 6 + 1] & 0xff) * 0x100 - MAX_PPS * 6;
            int leng = (pcmData[i * 6 + 2] & 0xff) + (pcmData[i * 6 + 3] & 0xff) * 0x100;

            // The temporary buffer is doubled in size.
            address *= 2;
            leng *= 2;

            int end_pps = address + leng;
            int start_pps = end_pps - 160; // 160 samples
            if (start_pps < address) start_pps = address;

            for (int j = start_pps; j < end_pps; j++) {
                //logger.log(Level.TRACE, "before%d".formatted(o[j]));
                o.set(j, (byte) ((o.get(j) & 0xff) - (j - start_pps) * 16 / (end_pps - start_pps)));
                if (o.get(j) < 0)
                    o.set(j, (byte) 0);
                //logger.log(Level.TRACE, "after%d".formatted(o[j]));
            }

        }
        ppsDt = ByteUtil.toByteArray(o);

        // Creating a Header
        List<Header> h = new ArrayList<>();
        for (int i = 0; i < MAX_PPS; i++) {
            Header p = new Header();
            p.address = ((pcmData[i * 6 + 0] & 0xff) + (pcmData[i * 6 + 1] & 0xff) * 0x100 - MAX_PPS * 6) * 2;
            p.length = ((pcmData[i * 6 + 2] & 0xff) + (pcmData[i * 6 + 3] & 0xff) * 0x100) * 2;
            p.toneOfs = pcmData[i * 6 + 4] & 0xff;
            p.volumeOfs = pcmData[i * 6 + 5] & 0xff;

            h.add(p);
        }
        ppsHd = h.toArray(Header[]::new);

        return 0;
    }

    public void reset() {
        ppsDt = null;
        ppsHd = null;
        singleFlag = false; // Single note mode?
        lowCpuCheckFlag = false; // Reproduce at half the frequency?
        keyonFlag = false; // during Keyon?
        dataOffset1 = -1;
        dataOffset2 = -1;
        dataXor1 = 0; // Current position (decimal part)
        dataXor2 = 0; // Current position (decimal part)
        tick1 = 0;
        tick2 = 0;
        tickXor1 = 0;
        tickXor2 = 0;
        dataSize1 = -1;
        dataSize2 = -1;
        volume1 = 0;
        volume2 = 0;
        keyoffVol = 0;
        emitTable = new int[16];
        interpolation = true;
        setVolume(0);
    }

    public int start(int samplingRate, BiConsumer<Integer, Integer> callback) {
        this.samplingRate = samplingRate;
        reset();

        if (callback != null) {
            real = true;
            psg = callback;
        }

        return samplingRate;
    }

    public int write(int port, int adr, int data) {
        switch (port) {
        case 0x00:
            reset();
            break;
        case 0x01:
            play(adr >> 8, adr, data);
            break;
        case 0x02:
            this.stop();
            break;
        case 0x03:
            return setParam(adr, data) ? 1 : 0;
        case 0x04:
            int04();
            break;
        }

        return 0;
    }
}
