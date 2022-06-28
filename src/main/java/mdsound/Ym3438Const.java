package mdsound;

public abstract class Ym3438Const {

    /** logsin table */
    public static final int[] logSinRom = new int[] {
            0x859, 0x6c3, 0x607, 0x58b, 0x52e, 0x4e4, 0x4a6, 0x471,
            0x443, 0x41a, 0x3f5, 0x3d3, 0x3b5, 0x398, 0x37e, 0x365,
            0x34e, 0x339, 0x324, 0x311, 0x2ff, 0x2ed, 0x2dc, 0x2cd,
            0x2bd, 0x2af, 0x2a0, 0x293, 0x286, 0x279, 0x26d, 0x261,
            0x256, 0x24b, 0x240, 0x236, 0x22c, 0x222, 0x218, 0x20f,
            0x206, 0x1fd, 0x1f5, 0x1ec, 0x1e4, 0x1dc, 0x1d4, 0x1cd,
            0x1c5, 0x1be, 0x1b7, 0x1b0, 0x1a9, 0x1a2, 0x19b, 0x195,
            0x18f, 0x188, 0x182, 0x17c, 0x177, 0x171, 0x16b, 0x166,
            0x160, 0x15b, 0x155, 0x150, 0x14b, 0x146, 0x141, 0x13c,
            0x137, 0x133, 0x12e, 0x129, 0x125, 0x121, 0x11c, 0x118,
            0x114, 0x10f, 0x10b, 0x107, 0x103, 0x0ff, 0x0fb, 0x0f8,
            0x0f4, 0x0f0, 0x0ec, 0x0e9, 0x0e5, 0x0e2, 0x0de, 0x0db,
            0x0d7, 0x0d4, 0x0d1, 0x0cd, 0x0ca, 0x0c7, 0x0c4, 0x0c1,
            0x0be, 0x0bb, 0x0b8, 0x0b5, 0x0b2, 0x0af, 0x0ac, 0x0a9,
            0x0a7, 0x0a4, 0x0a1, 0x09f, 0x09c, 0x099, 0x097, 0x094,
            0x092, 0x08f, 0x08d, 0x08a, 0x088, 0x086, 0x083, 0x081,
            0x07f, 0x07d, 0x07a, 0x078, 0x076, 0x074, 0x072, 0x070,
            0x06e, 0x06c, 0x06a, 0x068, 0x066, 0x064, 0x062, 0x060,
            0x05e, 0x05c, 0x05b, 0x059, 0x057, 0x055, 0x053, 0x052,
            0x050, 0x04e, 0x04d, 0x04b, 0x04a, 0x048, 0x046, 0x045,
            0x043, 0x042, 0x040, 0x03f, 0x03e, 0x03c, 0x03b, 0x039,
            0x038, 0x037, 0x035, 0x034, 0x033, 0x031, 0x030, 0x02f,
            0x02e, 0x02d, 0x02b, 0x02a, 0x029, 0x028, 0x027, 0x026,
            0x025, 0x024, 0x023, 0x022, 0x021, 0x020, 0x01f, 0x01e,
            0x01d, 0x01c, 0x01b, 0x01a, 0x019, 0x018, 0x017, 0x017,
            0x016, 0x015, 0x014, 0x014, 0x013, 0x012, 0x011, 0x011,
            0x010, 0x00f, 0x00f, 0x00e, 0x00d, 0x00d, 0x00c, 0x00c,
            0x00b, 0x00a, 0x00a, 0x009, 0x009, 0x008, 0x008, 0x007,
            0x007, 0x007, 0x006, 0x006, 0x005, 0x005, 0x005, 0x004,
            0x004, 0x004, 0x003, 0x003, 0x003, 0x002, 0x002, 0x002,
            0x002, 0x001, 0x001, 0x001, 0x001, 0x001, 0x001, 0x001,
            0x000, 0x000, 0x000, 0x000, 0x000, 0x000, 0x000, 0x000
    };

    /** exp table */
    public static final int[] expRom = new int[] {
            0x000, 0x003, 0x006, 0x008, 0x00b, 0x00e, 0x011, 0x014,
            0x016, 0x019, 0x01c, 0x01f, 0x022, 0x025, 0x028, 0x02a,
            0x02d, 0x030, 0x033, 0x036, 0x039, 0x03c, 0x03f, 0x042,
            0x045, 0x048, 0x04b, 0x04e, 0x051, 0x054, 0x057, 0x05a,
            0x05d, 0x060, 0x063, 0x066, 0x069, 0x06c, 0x06f, 0x072,
            0x075, 0x078, 0x07b, 0x07e, 0x082, 0x085, 0x088, 0x08b,
            0x08e, 0x091, 0x094, 0x098, 0x09b, 0x09e, 0x0a1, 0x0a4,
            0x0a8, 0x0ab, 0x0ae, 0x0b1, 0x0b5, 0x0b8, 0x0bb, 0x0be,
            0x0c2, 0x0c5, 0x0c8, 0x0cc, 0x0cf, 0x0d2, 0x0d6, 0x0d9,
            0x0dc, 0x0e0, 0x0e3, 0x0e7, 0x0ea, 0x0ed, 0x0f1, 0x0f4,
            0x0f8, 0x0fb, 0x0ff, 0x102, 0x106, 0x109, 0x10c, 0x110,
            0x114, 0x117, 0x11b, 0x11e, 0x122, 0x125, 0x129, 0x12c,
            0x130, 0x134, 0x137, 0x13b, 0x13e, 0x142, 0x146, 0x149,
            0x14d, 0x151, 0x154, 0x158, 0x15c, 0x160, 0x163, 0x167,
            0x16b, 0x16f, 0x172, 0x176, 0x17a, 0x17e, 0x181, 0x185,
            0x189, 0x18d, 0x191, 0x195, 0x199, 0x19c, 0x1a0, 0x1a4,
            0x1a8, 0x1ac, 0x1b0, 0x1b4, 0x1b8, 0x1bc, 0x1c0, 0x1c4,
            0x1c8, 0x1cc, 0x1d0, 0x1d4, 0x1d8, 0x1dc, 0x1e0, 0x1e4,
            0x1e8, 0x1ec, 0x1f0, 0x1f5, 0x1f9, 0x1fd, 0x201, 0x205,
            0x209, 0x20e, 0x212, 0x216, 0x21a, 0x21e, 0x223, 0x227,
            0x22b, 0x230, 0x234, 0x238, 0x23c, 0x241, 0x245, 0x249,
            0x24e, 0x252, 0x257, 0x25b, 0x25f, 0x264, 0x268, 0x26d,
            0x271, 0x276, 0x27a, 0x27f, 0x283, 0x288, 0x28c, 0x291,
            0x295, 0x29a, 0x29e, 0x2a3, 0x2a8, 0x2ac, 0x2b1, 0x2b5,
            0x2ba, 0x2bf, 0x2c4, 0x2c8, 0x2cd, 0x2d2, 0x2d6, 0x2db,
            0x2e0, 0x2e5, 0x2e9, 0x2ee, 0x2f3, 0x2f8, 0x2fd, 0x302,
            0x306, 0x30b, 0x310, 0x315, 0x31a, 0x31f, 0x324, 0x329,
            0x32e, 0x333, 0x338, 0x33d, 0x342, 0x347, 0x34c, 0x351,
            0x356, 0x35b, 0x360, 0x365, 0x36a, 0x370, 0x375, 0x37a,
            0x37f, 0x384, 0x38a, 0x38f, 0x394, 0x399, 0x39f, 0x3a4,
            0x3a9, 0x3ae, 0x3b4, 0x3b9, 0x3bf, 0x3c4, 0x3c9, 0x3cf,
            0x3d4, 0x3da, 0x3df, 0x3e4, 0x3ea, 0x3ef, 0x3f5, 0x3fa
    };

    /** note table */
    public static final int[] fnNote = new int[] {0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 3, 3, 3, 3, 3};

    /** Env Gen */
    public static final int[][] egStephi = new int[][] {
            new int[] {0, 0, 0, 0},
            new int[] {1, 0, 0, 0},
            new int[] {1, 0, 1, 0},
            new int[] {1, 1, 1, 0}
    };

    public static final byte[] egAmShift = {7, 3, 1, 0};

    /** Phase Gen */
    public static final int[] pgDetune = new int[] {16, 17, 19, 20, 22, 24, 27, 29};

    public static final int[][] pgLfoSh1 = new int[][] {
            new int[] {7, 7, 7, 7, 7, 7, 7, 7},
            new int[] {7, 7, 7, 7, 7, 7, 7, 7},
            new int[] {7, 7, 7, 7, 7, 7, 1, 1},
            new int[] {7, 7, 7, 7, 1, 1, 1, 1},
            new int[] {7, 7, 7, 1, 1, 1, 1, 0},
            new int[] {7, 7, 1, 1, 0, 0, 0, 0},
            new int[] {7, 7, 1, 1, 0, 0, 0, 0},
            new int[] {7, 7, 1, 1, 0, 0, 0, 0}
    };

    public static final int[][] pgLfoSh2 = new int[][] {
            new int[] {7, 7, 7, 7, 7, 7, 7, 7},
            new int[] {7, 7, 7, 7, 2, 2, 2, 2},
            new int[] {7, 7, 7, 2, 2, 2, 7, 7},
            new int[] {7, 7, 2, 2, 7, 7, 2, 2},
            new int[] {7, 7, 2, 7, 7, 7, 2, 7},
            new int[] {7, 7, 7, 2, 7, 7, 2, 1},
            new int[] {7, 7, 7, 2, 7, 7, 2, 1},
            new int[] {7, 7, 7, 2, 7, 7, 2, 1}
    };

    /** Address Decod */
    public static final int[] opOffset = new int[] {
            0x000, // Ch1 OP1/OP2
            0x001, // Ch2 OP1/OP2
            0x002, // Ch3 OP1/OP2
            0x100, // Ch4 OP1/OP2
            0x101, // Ch5 OP1/OP2
            0x102, // Ch6 OP1/OP2
            0x004, // Ch1 OP3/OP4
            0x005, // Ch2 OP3/OP4
            0x006, // Ch3 OP3/OP4
            0x104, // Ch4 OP3/OP4
            0x105, // Ch5 OP3/OP4
            0x106 // Ch6 OP3/OP4
    };

    public static final int[] chOffset = new int[] {
            0x000, // Ch1
            0x001, // Ch2
            0x002, // Ch3
            0x100, // Ch4
            0x101, // Ch5
            0x102 // Ch6
    };

    public static final int[] lfoCycles = {108, 77, 71, 67, 62, 44, 8, 5};

    public static final int[][][] fmAlgorithm = new int[][][] {
            new int[][] {
                    new int[] {1, 1, 1, 1, 1, 1, 1, 1}, // OP1_0
                    new int[] {1, 1, 1, 1, 1, 1, 1, 1}, // OP1_1
                    new int[] {0, 0, 0, 0, 0, 0, 0, 0}, // OP2
                    new int[] {0, 0, 0, 0, 0, 0, 0, 0}, // Last Operator
                    new int[] {0, 0, 0, 0, 0, 0, 0, 0}, // Last Operator
                    new int[] {0, 0, 0, 0, 0, 0, 0, 1}  // Out
            },
            new int[][] {
                    new int[] {0, 1, 0, 0, 0, 1, 0, 0}, // OP1_0
                    new int[] {0, 0, 0, 0, 0, 0, 0, 0}, // OP1_1
                    new int[] {1, 1, 1, 0, 0, 0, 0, 0}, // OP2
                    new int[] {0, 0, 0, 0, 0, 0, 0, 0}, // Last Operator
                    new int[] {0, 0, 0, 0, 0, 0, 0, 0}, // Last Operator
                    new int[] {0, 0, 0, 0, 0, 1, 1, 1}  // Out
            },
            new int[][] {
                    new int[] {0, 0, 0, 0, 0, 0, 0, 0}, // OP1_0
                    new int[] {0, 0, 0, 0, 0, 0, 0, 0}, // OP1_1
                    new int[] {0, 0, 0, 0, 0, 0, 0, 0}, // OP2
                    new int[] {1, 0, 0, 1, 1, 1, 1, 0}, // Last Operator
                    new int[] {0, 0, 0, 0, 0, 0, 0, 0}, // Last Operator
                    new int[] {0, 0, 0, 0, 1, 1, 1, 1}  // Out
            },
            new int[][] {
                    new int[] {0, 0, 1, 0, 0, 1, 0, 0}, // OP1_0
                    new int[] {0, 0, 0, 0, 0, 0, 0, 0}, // OP1_1
                    new int[] {0, 0, 0, 1, 0, 0, 0, 0}, // OP2
                    new int[] {1, 1, 0, 1, 1, 0, 0, 0}, // Last Operator
                    new int[] {0, 0, 1, 0, 0, 0, 0, 0}, // Last Operator
                    new int[] {1, 1, 1, 1, 1, 1, 1, 1}  // Out
            }
    };

    public enum Type {
        /**
         * Discrete YM3438 (Teradrive) (with ladderEffect)
         */
        discrete,
        /**
         * ASIC YM3438 (MD1 VA7, MD2, MD3, etc) (pure Sound)
         */
        asic,
        /**
         * Ym2612 (MD1, MD2 VA2) (with ladderEffect + Amplify signal + lowpassfilter)
         */
        ym2612,
        /**
         * Ym2612 without lowpass filter (with ladderEffect + Amplify signal)
         */
        ym2612_u,
        /**
         * ASIC with lowpass filter (with lowpassfilter)
         */
        asic_lp
    }

    public static Type chip_type = Type.asic;
    public static int use_filter = 0;
}
