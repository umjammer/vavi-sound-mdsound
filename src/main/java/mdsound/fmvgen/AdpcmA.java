package mdsound.fmvgen;

import mdsound.fmgen.Opna.OPNABase;
import mdsound.fmvgen.effect.ReversePhase;


public class AdpcmA {
    public OPNA2 parent = null;

    static class Channel {
        // ぱん
        public float panL;
        // ぱん
        public float panR;
        // おんりょう
        public byte level;
        // おんりょうせってい
        public int volume;
        // いち
        public int pos;
        // すてっぷち
        public int step;

        // 開始
        public int start;
        // 終了
        public int stop;
        // 次の 4 bit
        public int nibble;
        // 変換用
        public short adpcmX;
        // 変換用
        public short adpcmD;

        public void init() {
            this.panL = 1.0f;
            this.panR = 1.0f;
            this.level = 0;
            this.volume = 0;
            this.pos = 0;
            this.step = 0;
            this.volume = 0;
            this.start = 0;
            this.stop = 0;
            this.adpcmX = 0;
            this.adpcmD = 0;
        }

        public void keyOn() {
            this.pos = this.start;
            this.step = 0;
            this.adpcmX = 0;
            this.adpcmD = 0;
            this.nibble = 0;
        }

        public void pan(byte data) {
            this.panL = OPNA2.panTable[((data >> 5) & 3) & 3] * ((data >> 7) & 1);
            this.panR = OPNA2.panTable[((data >> 2) & 3) & 3] * ((data >> 4) & 1);
        }
    }

    public Channel[] channel = new Channel[] {
            new Channel(), new Channel(), new Channel(),
            new Channel(), new Channel(), new Channel()
    };

    // AdpcmA ROM
    public byte[] buf;
    public int size;
    // AdpcmA 全体の音量
    public byte tl;
    public int tVol;
    // AdpcmA のキー
    public byte key;
    public int step;
    public byte[] reg = new byte[32];
    public static short[] jediTable = new short[(48 + 1) * 16];

    private Fmvgen.Effects effects;
    private int revStartCh;
    private int num = 0;

    private static final byte[] table2 = new byte[] {
            1, 3, 5, 7, 9, 11, 13, 15,
            -1, -3, -5, -7, -9, -11, -13, -15,
    };

    private static final int[] decodeTableA1 = new int[] {
            -1 * 16, -1 * 16, -1 * 16, -1 * 16, 2 * 16, 5 * 16, 7 * 16, 9 * 16,
            -1 * 16, -1 * 16, -1 * 16, -1 * 16, 2 * 16, 5 * 16, 7 * 16, 9 * 16
    };
    private int currentCh;
    private boolean currentIsLSB;
    //protected float[] panTable = new float[4] { 1.0f, 0.5012f, 0.2512f, 0.1000f };

    public AdpcmA(int num, Fmvgen.Effects effects, int revStartCh) {
        this.num = num;
        this.effects = effects;

        this.revStartCh = revStartCh;
        this.buf = null;
        this.size = 0;
        for (int i = 0; i < 6; i++) {
            channel[i].init();
        }
        this.tl = 0;
        this.key = 0;
        this.tVol = 0;
    }

    static {
        for (int i = 0; i <= 48; i++) {
            int s = (int) (16.0 * Math.pow(1.1, i) * 3);
            for (int j = 0; j < 16; j++) {
                jediTable[i * 16 + j] = (short) (s * table2[j] / 8);
            }
        }
    }

    /**
     * AdpcmA 合成
     */
    public void mix(int[] buffer, int count) {

        if (tVol < 128 && (key & 0x3f) != 0) {
            int limit = count * 2;
            int revSampleL = 0;
            int revSampleR = 0;
            for (int i = 0; i < 6; i++) {
                Channel r = channel[i];
                if ((key & (1 << i)) != 0 && r.level < 128) {
                    //int maskl = (int)(r.panL == 0f ? -1 : 0);
                    //int maskr = (int)(r.panR == 0f ? -1 : 0);

                    int db = Fmvgen.limit(tl + tVol + r.level + r.volume, 127, -31);
                    int vol = OPNABase.tlTable[Fmvgen.FM_TLPOS + (db << (Fmvgen.FM_TLBITS - 7))] >> 4;

                    int dest = 0;
                    for (; dest < limit; dest += 2) {
                        r.step += step;
                        if (r.pos >= r.stop) {
                            //setStatus((int)(0x100 << i));
                            key &= (byte) ~(1 << i);
                            break;
                        }

                        for (; r.step > 0x10000; r.step -= 0x10000) {
                            int data;
                            if ((r.pos & 1) == 0) {
                                r.nibble = buf[r.pos >> 1];
                                data = r.nibble >> 4;
                            } else {
                                data = r.nibble & 0x0f;
                            }
                            r.pos++;

                            r.adpcmX += jediTable[r.adpcmD + data];
                            r.adpcmX = (short) Fmvgen.limit(r.adpcmX, 2048 * 3 - 1, -2048 * 3);
                            r.adpcmD += (short) decodeTableA1[data];
                            r.adpcmD = (short) Fmvgen.limit(r.adpcmD, 48 * 16, 0);
                        }

                        int[] sampleL = new int[] { (r.adpcmX * vol) >> 10 };
                        int[] sampleR = new int[] { (r.adpcmX * vol) >> 10 };
                        effects.distortion.mix(revStartCh + i, sampleL, sampleR);
                        effects.chorus.mix(revStartCh + i, sampleL, sampleR);
                        effects.hpflpf.mix(revStartCh + i, sampleL, sampleR);
                        effects.compressor.mix(revStartCh + i, sampleL, sampleR);

                        sampleL[0] = (int) (sampleL[0] * r.panL) * ReversePhase.adpcmA[i][0];
                        sampleR[0] = (int) (sampleR[0] * r.panR) * ReversePhase.adpcmA[i][1];
                        buffer[dest + 0] += sampleL[0];
                        buffer[dest + 1] += sampleR[0];
                        revSampleL += (int) (sampleL[0] * effects.reverb.sendLevel[revStartCh + i] * 0.6);
                        revSampleR += (int) (sampleR[0] * effects.reverb.sendLevel[revStartCh + i] * 0.6);
                    }
                }
            }
            effects.reverb.storeDataC(revSampleL, revSampleR);
        }
    }

    public void setReg(int adr, byte data) {
        switch (adr) {
        case 0x00: // DM/KEYON
            if ((data & 0x80) == 0) { // KEY ON
                key |= (byte) (data & 0x3f);
                for (int c = 0; c < 6; c++) {
                    if ((data & (1 << c)) != 0) {
                        //resetStatus((int)(0x100 << c));
                        channel[c].keyOn();
                    }
                }
            } else { // DUMP
                key &= (byte) ~data;
            }
            break;

        case 0x01:
            tl = (byte) (~data & 63);
            break;

        case 0x02:
            currentCh = data % 6;
            currentIsLSB = true;
            break;

        case 0x03:
            channel[currentCh].level = (byte) (~data & 31);
            break;

        case 0x04:
            channel[currentCh].pan(data);
            break;

        case 0x05:
            if (currentIsLSB) {
                channel[currentCh].start = data;
                currentIsLSB = false;
            } else {
                channel[currentCh].start |= data * 0x100;
                channel[currentCh].start <<= 9;
                currentIsLSB = true;
            }
            break;

        case 0x06:
            if (currentIsLSB) {
                channel[currentCh].stop = data;
                currentIsLSB = false;
            } else {
                channel[currentCh].stop |= data * 0x100;
                channel[currentCh].stop <<= 9;
                currentIsLSB = true;
            }
            break;
        }
    }
}
