// $Id: v 1.9 2016/05/08 01:01:29 saya Exp $

package mdsound;

import java.nio.ByteBuffer;
import java.util.Arrays;


public class MPcmX68k extends Instrument.BaseInstrument {
    @Override
    public String getName() {
        return "MPcmX68k";
    }

    @Override
    public String getShortName() {
        return "mpx";
    }

    @Override
    public void reset(byte chipID) {
        reset_(chipID);
    }

    @Override
    public int start(byte chipID, int clock) {
        return start(chipID, 44100, clock, null);
    }

    @Override
    public int start(byte chipID, int samplingRate, int clockValue, Object... option) {
        mountMpcmX68K(chipID);
        initialize(chipID, (int) clockValue, samplingRate);
        return samplingRate;
    }

    @Override
    public void stop(byte chipID) {
        unmountMpcmX68K(chipID);
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        update_(chipID, outputs, samples);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        return 0;
    }

    private static final int MAX_CHIPS = 0x02;
    public MPcm[] m = new MPcm[] {new MPcm(), new MPcm()};

    /*
     * MPCM (c)wachoman 互換えんじん(mndrvが使ってる機能のみ)
     *
     * ADPCMデコードはXM6をぱk参考にした．
     */
    public static class MPcm {

        public static class PCM {
            // -1:ADPCM 0:なし 1:16bit 2:8bit
            public byte type;
            // 基本note
            public byte orig;
            public byte[] adrsBuf;
            public int adrs_ptr;
            public int size;
            // ループ開始点
            public int start;
            // ループ終点
            public int end;
            // ループ回数(0:無限)
            public int count;
        }

        // 効果音用の chは未サポート
        static final int VOICE_MAX = 16;

        protected enum TYPE {
            _NONE(0),
            _16(1),
            _8(2),
            _ADPCM(0xff); // -1
            int v;

            TYPE(int v) {
                this.v = v;
            }

            static TYPE valueOf(int v) {
                return Arrays.stream(values()).filter(e -> e.v == v).findFirst().get();
            }
        }

        protected static final int TBL_DIFF = 49 * 16;

        public static class Channel {
            public boolean enable;
            public int vol;
            public int volWork;
            public int type;
            public int[] lr = new int[2];
            public int orig;
            public byte[] adrsBuf;
            public int adrsPtr;
            public int bufferSize;
            public int size;
            public int lpStart;
            public int lpEnd;
            public int lpCount;
            public int lpWork;
            public int pitch;
            public long pos;
            public long ppos;
            public int offset;
            public int sample;
            public int lpSample;
            public int lpOffset;

            public void reset() {
                this.sample = 0;
                this.ppos = -1;
                this.lr[0] = 1;
                this.lr[1] = 1;
            }

            public void keyOn() {
                this.enable = true;
                this.lpWork = 0;
                this.pos = 0;
                this.ppos = -1;
                this.sample = 0;
            }

            public void keyOff() {
                this.enable = false;
            }

            public void setPcm(PCM ptr) {
                this.type = ptr.type;
                this.orig = ptr.orig << 6;
                this.adrsBuf = ptr.adrsBuf;
                this.adrsPtr = ptr.adrs_ptr;
                this.size = ptr.size;
                this.lpStart = ptr.start;
                this.lpEnd = ptr.end;
                this.lpCount = ptr.count;
                this.pitch = 0x10000;

                if (this.lpEnd == 0) this.lpEnd = this.size;

                switch (TYPE.valueOf(ptr.type & 0xff)) {
                case _16:
                    this.size /= 2;
                    this.lpStart /= 2;
                    this.lpEnd /= 2;
                    break;
                case _ADPCM:
                    this.size *= 2;
                    this.lpStart *= 2;
                    this.lpEnd *= 2;
                    break;
                }
            }

            public void setVol(int volwork, int vol) {
                volwork &= 0x7f;
                this.volWork = volwork;
                this.vol = vol;
            }

            public void setPitch(int note, float base) {
                int orig = this.orig;
                int pitch = 0x10000;
                short doct = 0, dnote = 0;

                dnote = (short) note;

                if (orig > 0x1fc0) {
                    this.pitch = (int) (0x10000 * base);
                    return;
                }

                dnote -= (short) orig;
                if (dnote == 0) {
                    pitch = 0x10000;
                } else if (dnote > 0) {
                    for (dnote -= 64 * 12; dnote > 0; dnote -= 64 * 12, doct++) ;
                    dnote += 64 * 12;
                    pitch += pitchTbl[dnote];
                    pitch <<= doct;
                } else {
                    for (; dnote < 0; dnote += 64 * 12, doct++) ;
                    pitch += pitchTbl[dnote];
                    pitch >>= doct;
                }
                this.pitch = (int) (pitch * base);
            }

            public void setPan(int pan) {
                if (pan < 0x80) {
                    // 3段階
                    switch (pan) {
                    case 1:
                        this.lr[0] = 1;
                        this.lr[1] = 0;
                        break;
                    case 2:
                        this.lr[0] = 0;
                        this.lr[1] = 1;
                        break;
                    case 3:
                        this.lr[0] = 1;
                        this.lr[1] = 1;
                        break;
                    case 0:
                        this.lr[0] = 0;
                        this.lr[1] = 0;
                        break;
                    default:
                        break;
                    }
                } else {
                    // 128段階
                }
            }
        }

        public int[] volTbl;

        public static final int[][] VolTbl = new int[][] {
                new int[] {
                        16, 16, 16, 16, 16, 16, 16, 16,
                        24, 24, 24, 24, 24, 24, 24, 24,
                        32, 32, 32, 32, 32, 32, 32, 32,
                        40, 40, 40, 40, 40, 40, 40, 40,
                        48, 48, 48, 48, 48, 48, 48, 48,
                        64, 64, 64, 64, 64, 64, 64, 64,
                        80, 80, 80, 80, 80, 80, 80, 80,
                        96, 96, 96, 96, 96, 96, 96, 96,
                        128, 128, 128, 128, 128, 128, 128, 128,
                        160, 160, 160, 160, 160, 160, 160, 160,
                        192, 192, 192, 192, 192, 192, 192, 192,
                        256, 256, 256, 256, 256, 256, 256, 256,
                        320, 320, 320, 320, 320, 320, 320, 320,
                        384, 384, 384, 384, 384, 384, 384, 384,
                        512, 512, 512, 512, 512, 512, 512, 512,
                        640, 640, 640, 640, 640, 640, 640, 640
                },
                new int[] {
                        16, 17, 18, 19, 20, 21, 22, 23,
                        24, 25, 26, 27, 28, 29, 30, 31,
                        32, 33, 34, 35, 36, 37, 38, 39,
                        40, 41, 42, 43, 44, 45, 46, 47,
                        48, 50, 52, 54, 56, 58, 60, 62,
                        64, 66, 68, 70, 72, 74, 76, 78,
                        80, 82, 84, 86, 88, 90, 92, 94,
                        96, 100, 104, 108, 112, 116, 120, 124,
                        128, 132, 136, 140, 144, 148, 152, 156,
                        160, 164, 168, 172, 176, 180, 184, 188,
                        192, 200, 208, 216, 224, 232, 240, 248,
                        256, 264, 272, 280, 288, 296, 304, 312,
                        320, 328, 336, 344, 352, 360, 368, 376,
                        384, 400, 416, 432, 448, 464, 480, 496,
                        512, 528, 544, 560, 576, 592, 608, 624,
                        640, 656, 672, 688, 704, 720, 736, 752
                }
        };

        public void setVolTable(int sel, ByteBuffer tbl) {
            if (sel == 1) {
                // 16
                this.volTbl = VolTbl[0];
            } else {
                // 128
                this.volTbl = VolTbl[1];
            }
        }

        public Channel[] channels;
        public int[] diffTable;
        public float rate;
        public float base;
        public int mask = 0;

        // これ，計算で作ろうとすると合わないんだが．．．
        public static final int[] pitchTbl = new int[] {
                0x0000, 0x003b, 0x0076, 0x00b2, 0x00ed, 0x0128, 0x0164, 0x019f,
                0x01db, 0x0217, 0x0252, 0x028e, 0x02ca, 0x0305, 0x0341, 0x037d,
                0x03b9, 0x03f5, 0x0431, 0x046e, 0x04aa, 0x04e6, 0x0522, 0x055f,
                0x059b, 0x05d8, 0x0614, 0x0651, 0x068d, 0x06ca, 0x0707, 0x0743,
                0x0780, 0x07bd, 0x07fa, 0x0837, 0x0874, 0x08b1, 0x08ef, 0x092c,
                0x0969, 0x09a7, 0x09e4, 0x0a21, 0x0a5f, 0x0a9c, 0x0ada, 0x0b18,
                0x0b56, 0x0b93, 0x0bd1, 0x0c0f, 0x0c4d, 0x0c8b, 0x0cc9, 0x0d07,
                0x0d45, 0x0d84, 0x0dc2, 0x0e00, 0x0e3f, 0x0e7d, 0x0ebc, 0x0efa,
                0x0f39, 0x0f78, 0x0fb6, 0x0ff5, 0x1034, 0x1073, 0x10b2, 0x10f1,
                0x1130, 0x116f, 0x11ae, 0x11ee, 0x122d, 0x126c, 0x12ac, 0x12eb,
                0x132b, 0x136b, 0x13aa, 0x13ea, 0x142a, 0x146a, 0x14a9, 0x14e9,
                0x1529, 0x1569, 0x15aa, 0x15ea, 0x162a, 0x166a, 0x16ab, 0x16eb,
                0x172c, 0x176c, 0x17ad, 0x17ed, 0x182e, 0x186f, 0x18b0, 0x18f0,
                0x1931, 0x1972, 0x19b3, 0x19f5, 0x1a36, 0x1a77, 0x1ab8, 0x1afa,
                0x1b3b, 0x1b7d, 0x1bbe, 0x1c00, 0x1c41, 0x1c83, 0x1cc5, 0x1d07,
                0x1d48, 0x1d8a, 0x1dcc, 0x1e0e, 0x1e51, 0x1e93, 0x1ed5, 0x1f17,
                0x1f5a, 0x1f9c, 0x1fdf, 0x2021, 0x2064, 0x20a6, 0x20e9, 0x212c,
                0x216f, 0x21b2, 0x21f5, 0x2238, 0x227b, 0x22be, 0x2301, 0x2344,
                0x2388, 0x23cb, 0x240e, 0x2452, 0x2496, 0x24d9, 0x251d, 0x2561,
                0x25a4, 0x25e8, 0x262c, 0x2670, 0x26b4, 0x26f8, 0x273d, 0x2781,
                0x27c5, 0x280a, 0x284e, 0x2892, 0x28d7, 0x291c, 0x2960, 0x29a5,
                0x29ea, 0x2a2f, 0x2a74, 0x2ab9, 0x2afe, 0x2b43, 0x2b88, 0x2bcd,
                0x2c13, 0x2c58, 0x2c9d, 0x2ce3, 0x2d28, 0x2d6e, 0x2db4, 0x2df9,
                0x2e3f, 0x2e85, 0x2ecb, 0x2f11, 0x2f57, 0x2f9d, 0x2fe3, 0x302a,
                0x3070, 0x30b6, 0x30fd, 0x3143, 0x318a, 0x31d0, 0x3217, 0x325e,
                0x32a5, 0x32ec, 0x3332, 0x3379, 0x33c1, 0x3408, 0x344f, 0x3496,
                0x34dd, 0x3525, 0x356c, 0x35b4, 0x35fb, 0x3643, 0x368b, 0x36d3,
                0x371a, 0x3762, 0x37aa, 0x37f2, 0x383a, 0x3883, 0x38cb, 0x3913,
                0x395c, 0x39a4, 0x39ed, 0x3a35, 0x3a7e, 0x3ac6, 0x3b0f, 0x3b58,
                0x3ba1, 0x3bea, 0x3c33, 0x3c7c, 0x3cc5, 0x3d0e, 0x3d58, 0x3da1,
                0x3dea, 0x3e34, 0x3e7d, 0x3ec7, 0x3f11, 0x3f5a, 0x3fa4, 0x3fee,
                0x4038, 0x4082, 0x40cc, 0x4116, 0x4161, 0x41ab, 0x41f5, 0x4240,
                0x428a, 0x42d5, 0x431f, 0x436a, 0x43b5, 0x4400, 0x444b, 0x4495,
                0x44e1, 0x452c, 0x4577, 0x45c2, 0x460d, 0x4659, 0x46a4, 0x46f0,
                0x473b, 0x4787, 0x47d3, 0x481e, 0x486a, 0x48b6, 0x4902, 0x494e,
                0x499a, 0x49e6, 0x4a33, 0x4a7f, 0x4acb, 0x4b18, 0x4b64, 0x4bb1,
                0x4bfe, 0x4c4a, 0x4c97, 0x4ce4, 0x4d31, 0x4d7e, 0x4dcb, 0x4e18,
                0x4e66, 0x4eb3, 0x4f00, 0x4f4e, 0x4f9b, 0x4fe9, 0x5036, 0x5084,
                0x50d2, 0x5120, 0x516e, 0x51bc, 0x520a, 0x5258, 0x52a6, 0x52f4,
                0x5343, 0x5391, 0x53e0, 0x542e, 0x547d, 0x54cc, 0x551a, 0x5569,
                0x55b8, 0x5607, 0x5656, 0x56a5, 0x56f4, 0x5744, 0x5793, 0x57e2,
                0x5832, 0x5882, 0x58d1, 0x5921, 0x5971, 0x59c1, 0x5a10, 0x5a60,
                0x5ab0, 0x5b01, 0x5b51, 0x5ba1, 0x5bf1, 0x5c42, 0x5c92, 0x5ce3,
                0x5d34, 0x5d84, 0x5dd5, 0x5e26, 0x5e77, 0x5ec8, 0x5f19, 0x5f6a,
                0x5fbb, 0x600d, 0x605e, 0x60b0, 0x6101, 0x6153, 0x61a4, 0x61f6,
                0x6248, 0x629a, 0x62ec, 0x633e, 0x6390, 0x63e2, 0x6434, 0x6487,
                0x64d9, 0x652c, 0x657e, 0x65d1, 0x6624, 0x6676, 0x66c9, 0x671c,
                0x676f, 0x67c2, 0x6815, 0x6869, 0x68bc, 0x690f, 0x6963, 0x69b6,
                0x6a0a, 0x6a5e, 0x6ab1, 0x6b05, 0x6b59, 0x6bad, 0x6c01, 0x6c55,
                0x6caa, 0x6cfe, 0x6d52, 0x6da7, 0x6dfb, 0x6e50, 0x6ea4, 0x6ef9,
                0x6f4e, 0x6fa3, 0x6ff8, 0x704d, 0x70a2, 0x70f7, 0x714d, 0x71a2,
                0x71f7, 0x724d, 0x72a2, 0x72f8, 0x734e, 0x73a4, 0x73fa, 0x7450,
                0x74a6, 0x74fc, 0x7552, 0x75a8, 0x75ff, 0x7655, 0x76ac, 0x7702,
                0x7759, 0x77b0, 0x7807, 0x785e, 0x78b4, 0x790c, 0x7963, 0x79ba,
                0x7a11, 0x7a69, 0x7ac0, 0x7b18, 0x7b6f, 0x7bc7, 0x7c1f, 0x7c77,
                0x7ccf, 0x7d27, 0x7d7f, 0x7dd7, 0x7e2f, 0x7e88, 0x7ee0, 0x7f38,
                0x7f91, 0x7fea, 0x8042, 0x809b, 0x80f4, 0x814d, 0x81a6, 0x81ff,
                0x8259, 0x82b2, 0x830b, 0x8365, 0x83be, 0x8418, 0x8472, 0x84cb,
                0x8525, 0x857f, 0x85d9, 0x8633, 0x868e, 0x86e8, 0x8742, 0x879d,
                0x87f7, 0x8852, 0x88ac, 0x8907, 0x8962, 0x89bd, 0x8a18, 0x8a73,
                0x8ace, 0x8b2a, 0x8b85, 0x8be0, 0x8c3c, 0x8c97, 0x8cf3, 0x8d4f,
                0x8dab, 0x8e07, 0x8e63, 0x8ebf, 0x8f1b, 0x8f77, 0x8fd4, 0x9030,
                0x908c, 0x90e9, 0x9146, 0x91a2, 0x91ff, 0x925c, 0x92b9, 0x9316,
                0x9373, 0x93d1, 0x942e, 0x948c, 0x94e9, 0x9547, 0x95a4, 0x9602,
                0x9660, 0x96be, 0x971c, 0x977a, 0x97d8, 0x9836, 0x9895, 0x98f3,
                0x9952, 0x99b0, 0x9a0f, 0x9a6e, 0x9acd, 0x9b2c, 0x9b8b, 0x9bea,
                0x9c49, 0x9ca8, 0x9d08, 0x9d67, 0x9dc7, 0x9e26, 0x9e86, 0x9ee6,
                0x9f46, 0x9fa6, 0xa006, 0xa066, 0xa0c6, 0xa127, 0xa187, 0xa1e8,
                0xa248, 0xa2a9, 0xa30a, 0xa36b, 0xa3cc, 0xa42d, 0xa48e, 0xa4ef,
                0xa550, 0xa5b2, 0xa613, 0xa675, 0xa6d6, 0xa738, 0xa79a, 0xa7fc,
                0xa85e, 0xa8c0, 0xa922, 0xa984, 0xa9e7, 0xaa49, 0xaaac, 0xab0e,
                0xab71, 0xabd4, 0xac37, 0xac9a, 0xacfd, 0xad60, 0xadc3, 0xae27,
                0xae8a, 0xaeed, 0xaf51, 0xafb5, 0xb019, 0xb07c, 0xb0e0, 0xb145,
                0xb1a9, 0xb20d, 0xb271, 0xb2d6, 0xb33a, 0xb39f, 0xb403, 0xb468,
                0xb4cd, 0xb532, 0xb597, 0xb5fc, 0xb662, 0xb6c7, 0xb72c, 0xb792,
                0xb7f7, 0xb85d, 0xb8c3, 0xb929, 0xb98f, 0xb9f5, 0xba5b, 0xbac1,
                0xbb28, 0xbb8e, 0xbbf5, 0xbc5b, 0xbcc2, 0xbd29, 0xbd90, 0xbdf7,
                0xbe5e, 0xbec5, 0xbf2c, 0xbf94, 0xbffb, 0xc063, 0xc0ca, 0xc132,
                0xc19a, 0xc202, 0xc26a, 0xc2d2, 0xc33a, 0xc3a2, 0xc40b, 0xc473,
                0xc4dc, 0xc544, 0xc5ad, 0xc616, 0xc67f, 0xc6e8, 0xc751, 0xc7bb,
                0xc824, 0xc88d, 0xc8f7, 0xc960, 0xc9ca, 0xca34, 0xca9e, 0xcb08,
                0xcb72, 0xcbdc, 0xcc47, 0xccb1, 0xcd1b, 0xcd86, 0xcdf1, 0xce5b,
                0xcec6, 0xcf31, 0xcf9c, 0xd008, 0xd073, 0xd0de, 0xd14a, 0xd1b5,
                0xd221, 0xd28d, 0xd2f8, 0xd364, 0xd3d0, 0xd43d, 0xd4a9, 0xd515,
                0xd582, 0xd5ee, 0xd65b, 0xd6c7, 0xd734, 0xd7a1, 0xd80e, 0xd87b,
                0xd8e9, 0xd956, 0xd9c3, 0xda31, 0xda9e, 0xdb0c, 0xdb7a, 0xdbe8,
                0xdc56, 0xdcc4, 0xdd32, 0xdda0, 0xde0f, 0xde7d, 0xdeec, 0xdf5b,
                0xdfc9, 0xe038, 0xe0a7, 0xe116, 0xe186, 0xe1f5, 0xe264, 0xe2d4,
                0xe343, 0xe3b3, 0xe423, 0xe493, 0xe503, 0xe573, 0xe5e3, 0xe654,
                0xe6c4, 0xe735, 0xe7a5, 0xe816, 0xe887, 0xe8f8, 0xe969, 0xe9da,
                0xea4b, 0xeabc, 0xeb2e, 0xeb9f, 0xec11, 0xec83, 0xecf5, 0xed66,
                0xedd9, 0xee4b, 0xeebd, 0xef2f, 0xefa2, 0xf014, 0xf087, 0xf0fa,
                0xf16d, 0xf1e0, 0xf253, 0xf2c6, 0xf339, 0xf3ad, 0xf420, 0xf494,
                0xf507, 0xf57b, 0xf5ef, 0xf663, 0xf6d7, 0xf74c, 0xf7c0, 0xf834,
                0xf8a9, 0xf91e, 0xf992, 0xfa07, 0xfa7c, 0xfaf1, 0xfb66, 0xfbdc,
                0xfc51, 0xfcc7, 0xfd3c, 0xfdb2, 0xfe28, 0xfe9e, 0xff14, 0xff8a
        };

        public static final int[] NextTable = new int[] {
                -1, -1, -1, -1, 2, 4, 6, 8,
                -1, -1, -1, -1, 2, 4, 6, 8
        };

        public static final int[] OffsetTable = new int[] {
                0,
                0, 1, 2, 3, 4, 5, 6, 7,
                8, 9, 10, 11, 12, 13, 14, 15,
                16, 17, 18, 19, 20, 21, 22, 23,
                24, 25, 26, 27, 28, 29, 30, 31,
                32, 33, 34, 35, 36, 37, 38, 39,
                40, 41, 42, 43, 44, 45, 46, 47,
                48, 48, 48, 48, 48, 48, 48, 48,
                48
        };

        public void mount() {
            this.channels = new Channel[VOICE_MAX];
            for (int i = 0; i < VOICE_MAX; i++) {
                this.channels[i] = new Channel();
            }
            this.diffTable = new int[TBL_DIFF];
        }

        public void unmount() {
            this.diffTable = null;
            this.channels = null;
        }

        public boolean initialize(int base_, float samplingRate) {
            this.rate = samplingRate;
            this.base = (float) base_ / this.rate;

            makeTable();
            reset();

            return true;
        }

        public void reset() {
            this.volTbl = VolTbl[1];

            for (int ch = 0; ch < VOICE_MAX; ch++) {
                keyOff(ch);
                setVol(ch, 64);
                this.channels[ch].reset();
            }

            this.mask = 0;
        }

        // テーブル作成 (floor で丸めた方が panic 等で良い結果が得られる)
        private void makeTable() {
            int[] p = this.diffTable;
            int pPtr = 0;
            for (int i = 0; i < 49; i++) {
                int base_ = (int) Math.floor(16.0 * Math.pow(1.1, i));

                // 演算もすべて int で行う
                for (int j = 0; j < 16; j++) {
                    int diff = 0;
                    if ((j & 4) != 0) {
                        diff += base_;
                    }
                    if ((j & 2) != 0) {
                        diff += (base_ >> 1);
                    }
                    if ((j & 1) != 0) {
                        diff += (base_ >> 2);
                    }
                    diff += (base_ >> 3);
                    if ((j & 8) != 0) {
                        diff = -diff;
                    }

                    p[pPtr++] = diff;
                }
            }
        }

        public void keyOn(int ch) {
            if (ch == 0xff) {
                for (int i = 0; i < VOICE_MAX; i++) keyOn(i);
            } else {
                this.channels[ch].keyOn();
            }
        }

        public void keyOff(int ch) {
            if (ch == 0xff) {
                for (int i = 0; i < VOICE_MAX; i++) keyOff(i);
            } else {
                this.channels[ch].keyOff();
            }
        }

        public boolean setPcm(int ch, PCM ptr) {
            if (ch == 0xff) {
                for (int i = 0; i < VOICE_MAX; i++) setPcm(i, ptr);
            } else {
                keyOff(ch);

                this.channels[ch].setPcm(ptr);

                if (ptr.orig >= 0) {
                    setPitch(ch, this.channels[ch].orig);
                } else {
                    setPitch(ch, 440 << 6);
                }
            }
            return true;
        }

        public void setPitch(int ch, int note) {
            if (ch == 0xff) {
                for (int i = 0; i < VOICE_MAX; i++) setPitch(i, note);
            } else {
                this.channels[ch].setPitch(note, this.base);
            }
        }

        public void setVol(int ch, int vol) {
            if (ch == 0xff) {
                for (int i = 0; i < VOICE_MAX; i++) setVol(i, vol);
            } else {
                this.channels[ch].setVol(vol, volTbl[vol]);
            }
        }

        public void setPan(int ch, int pan) {
            if (ch == 0xff) {
                for (int i = 0; i < VOICE_MAX; i++) setPan(i, pan);
            } else {
                this.channels[ch].setPan(pan);
            }
        }

        private int decode(int ch, byte[] adrsBuf, int adrsPtr, long pos) {
            int index;
            byte data;
            long cnt = 0;
            long prev = this.channels[ch].ppos;
            int sample = this.channels[ch].sample;
            int offset = this.channels[ch].offset;
            int diff = 0;
            //int store = 0;
            //int poffset = 0;

            if (pos == prev) {
                return sample;
            }

            if (prev == -1) {
                // 初回だぜ．
                cnt = pos;
                prev = 0;
                offset = 0;
                this.channels[ch].lpSample = 0;
                this.channels[ch].lpOffset = 0;
            } else {
                cnt = pos - prev;
            }

            for (long c = 0; c < cnt; c++) {
                data = adrsBuf[(int) (adrsPtr + ((prev + c) >> 1))];
                if (((prev + c) & 1) != 0) {
                    data = (byte) ((data >> 4) & 0x0f);
                } else {
                    data &= 0x0f;
                }

                // 差分テーブルから得る
                index = offset << 4;
                index |= data;
                diff = this.diffTable[index];

                // ストアデータを演算
                sample += diff;
                if (sample > 2047) sample = 2047;
                if (sample < -2048) sample = -2048;

                // 偶数番値の場合はループ位置判定をする
                if (((prev + c) & 1) == 0 && (this.channels[ch].lpStart == pos)) {
                    this.channels[ch].lpSample = sample;
                    this.channels[ch].lpOffset = offset;
                }

                // 次のオフセットを求めておく
                offset += NextTable[data & 7];
                offset = OffsetTable[offset + 1];
            }

            this.channels[ch].offset = offset;
            this.channels[ch].sample = sample;
            this.channels[ch].ppos = pos;

            return sample;
        }

        public void update(int[][] _buffer, int _count) {

            for (int i = 0; i < _count; i++) {
                _buffer[0][i] = 0;
                _buffer[1][i] = 0;
            }

            for (int ch = 0; ch < VOICE_MAX; ch++) {
                boolean mute = (this.mask & (1 << ch)) != 0;
                short bufPtr = 0;

                byte[] ptr_buf = this.channels[ch].adrsBuf;
                int ptr_ptr = this.channels[ch].adrsPtr;
                long ofst = this.channels[ch].pos;
                int pitch = this.channels[ch].pitch;
                for (int bufsize = 0; (bufsize < _count) && (this.channels[ch].enable); bufsize++) {
                    int sample = 0;
                    long pos = ofst >> 16;

                    switch (TYPE.valueOf(this.channels[ch].type)) {
                    case _NONE:
                        break;
                    case _ADPCM:
                        sample = decode(ch, ptr_buf, ptr_ptr, pos);
                        break;
                    case _16:
                        sample = (short) ((ptr_buf[(int) (ptr_ptr + pos * 2)] << 8) + ptr_buf[(int) (ptr_ptr + pos * 2 + 1)]);
                        break;
                    case _8:
                        sample = ptr_buf[(int) (ptr_ptr + pos)];
                        break;
                    }
                    sample = (sample * this.channels[ch].vol) >> 3;

                    if (!mute) {
                        _buffer[0][bufPtr] += (short) (sample * this.channels[ch].lr[0]);
                        _buffer[1][bufPtr] += (short) (sample * this.channels[ch].lr[1]);
                        bufPtr++;
                    }

                    ofst += pitch;
                    if ((ofst >> 16) > this.channels[ch].lpEnd) {
                        this.channels[ch].lpWork++;
                        if ((this.channels[ch].lpCount != 0) && (this.channels[ch].lpWork >= this.channels[ch].lpCount)) {
                            keyOff(ch);
                        } else {
                            ofst &= 0xffff;
                            ofst += (this.channels[ch].lpStart << 16);
                            this.channels[ch].ppos = ofst;
                            this.channels[ch].sample = this.channels[ch].lpSample;
                            this.channels[ch].offset = this.channels[ch].lpOffset;
                        }
                    }
                }
                this.channels[ch].pos = ofst;
            }
        }

        public int getRegs(byte[] _buffer, int _bufferPtr, int _count, int _offset) {
            return 0;
        }

        public int getBufferCount() {
            return 1;
        }

        public short getBufferFlag(int _b) {
            return 0;
        }

        public int getTrackCount() {
            return VOICE_MAX;
        }
    }

    public void mountMpcmX68K(int chipID) {
        MPcm chip = m[chipID];
        chip.mount();
    }

    public void unmountMpcmX68K(int chipID) {
        MPcm chip = m[chipID];
        chip.unmount();
    }

    public boolean initialize(int chipID, int base_, float samplingRate) {
        MPcm chip = m[chipID];
        return chip.initialize(base_, samplingRate);
    }

    public void reset_(int chipID) {
        MPcm chip = m[chipID];
        chip.reset();
    }

    public void keyOn(int chipID, int ch) {
        MPcm chip = m[chipID];
        chip.keyOn(ch);
    }

    public void keyOff(int chipID, int ch) {
        MPcm chip = m[chipID];
        chip.keyOff(ch);
    }

    public boolean setPcm(int chipID, int ch, MPcm.PCM ptr) {
        MPcm chip = m[chipID];
        return chip.setPcm(ch, ptr);
    }

    public void setPitch(int chipID, int ch, int note) {
        MPcm chip = m[chipID];
        chip.setPitch(ch, note);
    }

    public void setVol(int chipID, int ch, int vol) {
        MPcm chip = m[chipID];
        chip.setVol(ch, vol);
    }

    public void setPan(int chipID, int ch, int pan) {
        MPcm chip = m[chipID];
        chip.setPan(ch, pan);
    }

    public void setVolTable(int chipID, int sel, ByteBuffer tbl) {
        MPcm chip = m[chipID];
        chip.setVolTable(sel, tbl);
    }

    private int decode(int chipID, int ch, byte[] adrs_buf, int adrs_ptr, long pos) {
        MPcm chip = m[chipID];
        return chip.decode(ch, adrs_buf, adrs_ptr, pos);
    }

    public void update_(int chipID, int[][] _buffer, int _count) {
        MPcm chip = m[chipID];
        chip.update(_buffer, _count);
    }
}
