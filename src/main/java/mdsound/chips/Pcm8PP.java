package mdsound.chips;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import static java.lang.System.getLogger;


public class Pcm8PP {

    //
    // The existing processing part uses X68Sound's PCM8.cs as is.
    //
    // Unimplemented status
    // number Data Format              output     specification
    //  7H  16bit Signed PCM(Through)  Monoural Depends on playback frequency // I don't really know how it works
    //  FH  Valiabled 16bit Signed PCM Monoural The frequency can be changed #1 // I don't think it's used in zmusic
    // 17H  Valiabled  8bit Signed PCM Monoural The frequency can be changed #1 // I don't think it's used in zmusic
    // 1FH  Valiabled 16bit Signed PCM Stereo   The frequency can be changed #1 // I don't think it's used in zmusic
    // 27H  Valiabled  8bit Signed PCM Stereo   The frequency can be changed #1 // I don't think it's used in zmusic
    // 28H  Valiabled ADPCM            Monoural The frequency can be changed #2 // I don't think it's used in zmusic
    // 29H  Valiabled 16bit Signed PCM Monoural The frequency can be changed #2 // I don't think it's used in zmusic
    //

    private static final Logger logger = getLogger(Pcm8PP.class.getName());

    private static class Channel {

        private boolean play = false;
        private int mode = 0;
        private int adrsPtr = 0;
        private int len = 0;
        private int endAdrs = 0;
        private double freq = 0;
        private double freqPerSampleRate = 0;
        private int outs = 0;
        private int type = 0;
        private int volume = 0;
        private int pan = 3;
        private double step = 0;
        private int pcmKind = 0;
        private boolean n1DataFlag = false;
        private byte n1Data = 0;
        private int inpPcm = 0;
        private int inpPcmPrev = 0;
        private int outPcm = 0;
        private int pcm = 0;
        private int scale = 0;
        private int pcm16Prev = 0;
        private boolean adpcmUpdate = true;
        private boolean mute = false;
    }

    private Channel[] ch;
    private byte[] mem;
    private double sampleRate;
    private double baseClock;

    private static final double[] freqTable = {
            // ADPCM mono
            3906.2, // 0
            5208.0, // 1
            7812.5, // 2
            10416.7, // 3
            15625.0, // 4
            // 16bit signed PCM mono
            15625.0, // 5
            // 8bit signed PCM mono
            15625.0, // 6
            // 16bit signed PCM (Through) mono
            -1, // 7
            // 16bit signed PCM mono
            15625.0, // 8
            16000.0, // 9
            22050.0, // 10
            24000.0, // 11
            32000.0, // 12
            44100.0, // 13
            48000.0, // 14
            -1, // 15
            // 8bit signed PCM mono
            15625.0, // 16
            16000.0, // 17
            22050.0, // 18
            24000.0, // 19
            32000.0, // 20
            44100.0, // 21
            48000.0, // 22
            -1, // 23
            // 16bit signed PCM stereo
            15625.0, // 24
            16000.0, // 25
            22050.0, // 26
            24000.0, // 27
            32000.0, // 28
            44100.0, // 29
            48000.0, // 30
            -1, // 31
            // 8bit signed PCM stereo
            15625.0, // 32
            16000.0, // 33
            22050.0, // 34
            24000.0, // 35
            32000.0, // 36
            44100.0, // 37
            48000.0, // 38
            -1, // 39
            // variabled ADPCM mono
            -1, // 40
            // variabled 16bit signed PCM mono
            -1 // 41
    };
    private static final int[] outsTable = {
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1,
            2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2,
            1, 1
    };
    private static final int[] typeTable = {
            0, 0, 0, 0, 0,
            2, 1, 2,
            2, 2, 2, 2, 2, 2, 2, 2,
            1, 1, 1, 1, 1, 1, 1, 1,
            2, 2, 2, 2, 2, 2, 2, 2,
            1, 1, 1, 1, 1, 1, 1, 1,
            0, 2
    };
    private static final int[] volTable = {
            2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 32, 40, 48, 64, 80,
    };
    private static final int[] dltLTBL = {
            16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66,
            73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
            337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552,
    };
    private static final int[] DCT = {
            -1, -1, -1, -1, 2, 4, 6, 8,
            -1, -1, -1, -1, 2, 4, 6, 8,
    };
    private static final int MAXPCMVAL = 2047;
    private int sOption = -1; // -1: default
    private static final int[] sOpTable = {
            28, 36, // s0: 16s32k    8s32k
            29, 37, // s1: 16s44.1k  8s44.1k
            30, 38, // s2: 16s48k    8s48k
            25, 33, // s3: 16s16k    8s16k
            26, 34, // s4: 16s22.05k 8s22.05k
            27, 35, // s5: 16s24k    8s24k

            12, 20, // s6: 16m32k    8m32k
            13, 21, // s7: 16m44.1k  8m44.1k
            14, 22, // s8: 16m48k    8m48k
             9, 17, // s9: 16m16k    8m16k
            10, 18, // sA: 16m22.05k 8m22.05k
            11, 19, // sB: 16m24k    8m24k
    };

    public void reset() {
        ch = new Channel[16];
        for (int i = 0; i < ch.length; i++) {
            ch[i] = new Channel();
        }
    }

    public int start(int sampleRate, int clock, int option) {
        this.sampleRate = sampleRate;
logger.log(Level.INFO, "sampleRate: " + sampleRate);
        baseClock = clock;

        sOption = option;

        return sampleRate;
    }

    public void update(int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            // Clear Buffer
            outputs[0][i] = 0;
            outputs[1][i] = 0;

            for (Channel channel : ch) {
                // If not, proceed to process the next channel
                if (!channel.play) continue;

                Channel st = channel;
                int valL;
                int valR;
                switch (st.pcmKind) {
                    // Processing of pcm8 (existing)
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                        // ADPCM mono
                        if (st.adpcmUpdate) {
                            st.adpcmUpdate = false;
                            if (!st.n1DataFlag) {
                                int n10Data;
                                if (mem.length <= st.adrsPtr) n10Data = 0;
                                else n10Data = mem[st.adrsPtr] & 0xff;
                                adpcm2pcm(st, (byte) (n10Data & 0x0F));
                                st.n1Data = (byte) ((n10Data >> 4) & 0x0F);
                            } else {
                                adpcm2pcm(st, st.n1Data);
                            }
                            st.outPcm = ((st.inpPcm << 9) - (st.inpPcmPrev << 9) + 459 * st.outPcm) >> 9;
                            st.inpPcmPrev = st.inpPcm;
                        }
                        valR = valL = ((st.outPcm * st.volume) >> 8);
                        break;
                    case 5:
                        // 16bit signed PCM mono
                        if (mem.length <= st.adrsPtr) valL = 0;
                        else valL = (short) (((mem[st.adrsPtr] & 0xff) << 8) + (mem[st.adrsPtr + 1] & 0xff));
                        // Volume Reflection
                        valL = valL * st.volume;
                        valL >>= 3; // 3 sloppy
                        valR = valL;
                        break;
                    case 6: // 8bit signed PCM mono
                        if (mem.length <= st.adrsPtr) valL = 0;
                        else valL = mem[st.adrsPtr] & 0xff;
                        // Volume Reflection
                        valL = valL * st.volume;
//                        valL <<= 5;
//                        valL = valL >> 3; // 3 sloppy
                        valL <<= 2;
                        valR = valL;
                        break;
                    // 7以降は新規実装
                    default:
                        // Processing of pcm8pp
                        switch (st.type) {
                            case 2:
                                // Audio data processing
//                                if (mem.length <= st.adrsPtr) valL = 0;
//                                else valL = mem[st.adrsPtr];

                                if (mem.length <= st.adrsPtr + 1) valL = 0;
                                else valL = (short) (((mem[st.adrsPtr] & 0xff) << 8) + (mem[st.adrsPtr + 1] & 0xff));

                                // Volume Reflection
                                valL = valL * st.volume;
                                valL >>= 3; // 3 sloppy
                                if (st.outs == 1) {
                                    valR = valL;
                                } else {
                                    if (mem.length <= st.adrsPtr + 2) valR = 0;
                                    else valR = (short) (((mem[st.adrsPtr + 2] & 0xff) << 8) + (mem[st.adrsPtr + 3] & 0xff));
                                    // Volume Reflection
                                    valR *= st.volume;
                                    valR >>= 3;
                                }
                                break;
                            default:
                                // Audio data processing
                                if (mem.length <= st.adrsPtr) valL = 0;
                                else valL = /* signed */ mem[st.adrsPtr];

                                // Volume Reflection
                                valL *= st.volume;
                                valL <<= 2;
                                if (st.outs == 1) {
                                    valR = valL;
                                } else {
                                    if (mem.length <= st.adrsPtr + 1) valR = 0;
                                    else
                                        valR = /* signed */ mem[st.adrsPtr + 1];
                                    // Volume Reflection
                                    valR *= st.volume;
                                    valR <<= 2;
                                }
                                break;
                        }
                        break;
                }

                // Store in buffer (add)
                if (!st.mute) {
                    outputs[0][i] += valL * ((st.pan & 1) != 0 ? 1 : 0);
                    outputs[1][i] += valR * ((st.pan & 2) != 0 ? 1 : 0);
                }

                // Pointer movement
                st.step += st.freqPerSampleRate;

                while (st.step >= 1.0) {
                    if (st.type != 0) {
                        st.adrsPtr += st.type;
                        if (st.outs != 1) st.adrsPtr += st.type;
                    } else {
                        st.n1DataFlag = !st.n1DataFlag;
                        if (!st.n1DataFlag)
                            st.adrsPtr++;
                        st.adpcmUpdate = true;
                    }
                    st.step -= 1.0;
                }
                // Play ends when the end position is reached
                if (Integer.compareUnsigned(st.adrsPtr, st.endAdrs) >= 0)
                    st.play = false;
            }
        }
    }

    public void keyOn(int c, int adrsPtr, int mode, int len, int d3Freq /* = 0 */) {
        ch[c].n1DataFlag = false;
        ch[c].n1Data = 0;
        ch[c].inpPcm = 0;
        ch[c].inpPcmPrev = 0;
        ch[c].outPcm = 0;
        ch[c].pcm = 0;
        ch[c].scale = 0;
        ch[c].pcm16Prev = 0;
        ch[c].adpcmUpdate = true;

        int v = (mode >> 16) & 0xff;
        if (v != 0xff) {
            v &= 0xf;
            ch[c].volume = volTable[v];
            ch[c].mode = (ch[c].mode & 0xff00_ffff) | (v << 16);
        }
        int m = (mode >> 8) & 0xff;
        if (m != 0xff) {

            if (sOption != -1 && (m == 5 || m == 6)) {
                m = sOpTable[sOption * 2 + (m - 5)];
            }

            ch[c].pcmKind = m;
            ch[c].freq = freqTable[m];
            ch[c].outs = outsTable[m];
            ch[c].type = typeTable[m];
            ch[c].mode = (ch[c].mode & 0xffff_00ff) | (m << 8);
            if (freqTable[m] < 0 && m >= 0xf) {
                ch[c].freq = d3Freq / 256.0;
            }
            ch[c].freqPerSampleRate = ch[c].freq / sampleRate;
        }
        int p = mode & 0xff;
        if (p != 0xff) {
            if ((p & 3) != 0) {
                ch[c].play = true;
                ch[c].adrsPtr = adrsPtr;
                ch[c].len = len;
                ch[c].endAdrs = adrsPtr + len;
                ch[c].pan = p;
                ch[c].mode = (ch[c].mode & 0xffff_ff00) | (p << 0);
            } else {
                ch[c].play = false;
            }
        }
    }

    public void keyOff(int c) {
        ch[c].play = false;
        ch[c].adrsPtr = 0;
        ch[c].mode = 0;
        ch[c].len = 0;
        ch[c].endAdrs = 0;
    }

    public void setMute(int c, boolean b) {
        ch[c].mute = b;
    }

    public void setMask(int n) {
        n >>= 8;
        for (int i = 0; i < 16; i++) {
            setMute(i, ((n >> i) & 1) != 0);
        }
    }

    public void mountMemory(byte[] mem) {
        this.mem = mem;
    }

    // Enter adpcm to change the value of inpPcm
    // -2047<<(4+4) <= inpPcm <= +2047<<(4+4)
    private void adpcm2pcm(Channel st, byte adpcm) {
        int dltL;
        dltL = dltLTBL[st.scale];
        dltL = (dltL & ((adpcm & 4) != 0 ? -1 : 0)) +
                ((dltL >> 1) & ((adpcm & 2) != 0 ? -1 : 0)) +
                ((dltL >> 2) & ((adpcm & 1) != 0 ? -1 : 0)) + (dltL >> 3);
        int sign = (adpcm & 8) != 0 ? -1 : 0;
        dltL = (dltL ^ sign) + (sign & 1);
        st.pcm += dltL;

        if (((st.pcm + MAXPCMVAL) & 0xffff_ffffL) > ((MAXPCMVAL * 2) & 0xffff_ffffL)) {
            if ((st.pcm + MAXPCMVAL) >= (MAXPCMVAL * 2)) {
                st.pcm = MAXPCMVAL;
            } else {
                st.pcm = -MAXPCMVAL;
            }
        }

        st.inpPcm = (st.pcm & -4) << (4 + 4); // (int) 0xffff_fffc

        st.scale += DCT[adpcm];
        if ((st.scale & 0xffff_ffffL) > 48L) {
            if (st.scale >= 48) {
                st.scale = 48;
            } else {
                st.scale = 0;
            }
        }
    }

    // Input pcm16 to change the value of inpPcm
    // -2047<<(4+4) <= inpPcm <= +2047<<(4+4)
    private void pcm16_2pcm(Channel st, int pcm16) {
        st.pcm += pcm16 - st.pcm16Prev;
        st.pcm16Prev = pcm16;

        if (((st.pcm + MAXPCMVAL) & 0xffff_ffffL) > ((MAXPCMVAL * 2) & 0xffff_ffffL)) {
            if ((st.pcm + MAXPCMVAL) >= (MAXPCMVAL * 2)) {
                st.pcm = MAXPCMVAL;
            } else {
                st.pcm = -MAXPCMVAL;
            }
        }

        st.inpPcm = (st.pcm & -4) << (4 + 4); // (int) 0xffff_fffc
    }
}
