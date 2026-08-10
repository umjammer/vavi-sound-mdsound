package mdsound.chips;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


/**
 * This CS4231 emulator was developed based on and ported from the following sources and materials:
 *
 * My App/iv, primarily PLAY4
 * CS4231A data sheet
 *
 * PC-9801-118
 * PCM (Windows Sound System)
 */
public class Cs4231 {

    private int indexAddress;
    private int indexData;
    private int status;
    private int PIOData;
    private final int[] reg = new int[32];
    private int dmaInt;
    public int renderingFreq;
    private final short[] sound = new short[2];
    private final short[][] sound2 = {
            new short[2], new short[2], new short[2], new short[2], new short[2],
            new short[2], new short[2], new short[2], new short[2], new short[2]
    };
    private static final int[] xtal = {24_576_000, 16_934_400};
    private static final int[] divTbl = {3072, 1536, 896, 768, 448, 384, 512, 2560};
    public final DMA dma = new DMA();
    private int imr = 0;
    private double step = 0;
    private double counter = 0;

    public void update(int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            this.step = ((double) xtal[this.reg[8] & 1] / divTbl[(this.reg[8] & 0xe) >> 1]) / this.renderingFreq;

            this.counter += this.step;
            short rcnt = 0;
            while (this.counter >= 1.0) {
                this.counter -= 1.0;
                exec(rcnt);
                rcnt++;
                //ch[0]._stat = 1;
            }
            synth(rcnt);

            outputs[0][i] = this.sound[0];
            outputs[1][i] = this.sound[1];
        }
    }

    private boolean latch = true;

    public int write(int port, int adr, int data) {
        if (port == 0) {
            switch (adr) {
                case 0:
                    this.indexAddress = data & 0xff;
                    break;
                case 1:
                    this.indexData = data & 0xff;
                    this.reg[this.indexAddress & 0x1f] = this.indexData;
                    break;
                case 2:
                    //status = dat;
                    resetIntFlag();
                    break;
                case 3:
                    this.PIOData = data & 0xff;
                    break;
                case 4:
                    this.dmaInt = data & 0xff;
                    break;
            }
        } else if (port == 1) {
            switch (adr) {
                case 0x2:
                    this.imr = data & 0xff;
                    break;
                case 0x5:
                    this.dma.writeReg(5, data & 0xff);
                    break;
                case 0x7:
                    this.dma.writeReg(7, data & 0xff);
                    break;
            }
        } else if (port == 2) {
            if (adr >= 200) {
                switch (adr) {
                    case 200:
                        if (latch) this.dma.freq2 = data & 0xff;
                        else this.dma.freq2 |= ((data & 0xff) << 8) & 0xffff;
                        latch = !latch;
                        return 0;
                    case 201:
                        if (latch) this.dma.jump1_ = data & 0xff;
                        else this.dma.jump1_ |= ((data & 0xff) << 8) & 0xffff;
                        latch = !latch;
                        return 0;
                    case 202:
                        this.dma.jump2_ = data & 0xff;
                        return 0;
                }
            }

            int ch = adr / (5 * 2);
            int prm = (adr % (5 * 2)) / 2;
            int ind = adr % 2;
            int dat = data & 0xff;
            switch (prm) {
                case 0: // Start address
                    if (latch) this.dma.pcm0work[ch].pcm0adrs[ind] = dat;
                    else this.dma.pcm0work[ch].pcm0adrs[ind] |= (dat << 8) & 0xffff;
                    latch = !latch;
                    break;
                case 1: // Playback length
                    if (latch) this.dma.pcm0work[ch].pcm0cnt[ind] = dat;
                    else this.dma.pcm0work[ch].pcm0cnt[ind] |= (dat << 8) & 0xffff;
                    latch = !latch;
                    break;
                case 2: // Frequency
                    if (latch) this.dma.pcm0work[ch].pcm0freq[ind] = dat;
                    else this.dma.pcm0work[ch].pcm0freq[ind] |= (dat << 8) & 0xffff;
                    latch = !latch;
                    break;
                case 3: // Pan
                    if (latch) this.dma.pcm0work[ch].pcm0pan[ind] = dat;
                    else this.dma.pcm0work[ch].pcm0pan[ind] |= (dat << 8) & 0xffff;
                    latch = !latch;
                    break;
                case 4: // Volume
                    if (latch) this.dma.pcm0work[ch].pcm0vol[ind] = dat;
                    else this.dma.pcm0work[ch].pcm0vol[ind] |= (dat << 8) & 0xffff;
                    latch = !latch;
                    break;
            }
        }

        return 0;
    }

    public void mutePcm(int ch, boolean sw) {
        if (ch < 0 || ch >= this.dma.pcm0work.length) return;
        this.dma.pcm0work[ch].mask = sw;
    }

    public int readReg(int adr) {
        return switch (adr) {
            case 0 -> this.indexAddress;
            case 1 -> this.reg[this.indexAddress & 0x1f];
            case 2 -> this.status;
            case 3 -> this.PIOData;
            case 4 -> this.dmaInt;
            case 5 -> this.imr;
            default -> 0;
        };
    }

    public void setFifoBuf(byte[] buf) {
        for (int i = 0; i < buf.length; i++) {
            dma.fifoBuf = buf;
        }
    }

    public byte[] EMS_GetCurrentMapBuf() {
        return dma.ems.getCurrentMapBuf();
    }

    public void EMS_Map(int al, byte[] ah, int bx, int dx) {
        dma.ems.map(al, ah, bx, dx);
    }

    public int EMS_GetPageMap() {
        return dma.ems.getPageMap();
    }

    public void EMS_GetHandleName(byte[] ah, int dx, String[] buf) {
        dma.ems.getHandleName(ah, dx, buf);
    }

    public void EMS_SetHandleName(byte[] ah, int dx, String emsName2) {
        dma.ems.setHandleName(ah, dx, emsName2);
    }

    public void EMS_AllocMemory(byte[] ah, int[] dx, int bx) {
        dma.ems.allocMemory(ah, dx, bx);
        dma.phandle = dx[0];
    }

//    public void setInt0bEnt(byte ChipID, Runnable callback) {
//        dma.int0bEnt = callback;
//    }

    private void exec(short rcnt) {
        short dat0 = (short) (((this.dma.getData() & 0xff) - 0x80) * 380);
        short dat1 = (short) (((this.dma.getData() & 0xff) - 0x80) * 380);
        this.sound2[rcnt][0] = dat0;
        this.sound2[rcnt][1] = dat1;
    }

    private void synth(short rcnt) {
        if (rcnt <= 0) return;
        int s0 = 0;
        int s1 = 0;
        for (int i = 0; i < Math.min(rcnt, this.sound2.length); i++) {
            s0 += this.sound2[i][0];
            s1 += this.sound2[i][1];
        }
        this.sound[0] = (short) (s0 / rcnt);
        this.sound[1] = (short) (s1 / rcnt);
    }

    private void resetIntFlag() {
        //
    }

    public static class DMA {

        //private Work work;
        private int ptr;
        private int cnt;
        public byte[] fifoBuf = new byte[FIFO_SIZE * MAXBUF * 2];
//        public Runnable int0bEnt;
        private boolean latch = true;

        DMA() {
            this.ptr = 0;
            this.cnt = 0;
            Arrays.fill(fifoBuf, (byte) 0x80);
            //this.fifoBuf = fifoBuf;
            //this.int0bEnt = int0bEnt;
        }

        byte getData() {
            if (fifoBuf == null) return (byte) 0x80;

            byte dat = fifoBuf[ptr];
//            if (dat == null) return (byte) 0x80;

            ptr++;
            cnt--;

            if (ptr == fifoBuf.length || cnt <= 0) {
                int0bEnt();
                if (ptr == fifoBuf.length) {
                    ptr = 0;
                }
            }

            return dat;
        }

        void writeReg(int l, int al) {
            if (l == 5) {
                if (latch) {
                    ptr = al;
                } else {
                    ptr = (ptr & 0xff) | (al * 0x100);
                }
                latch = !latch;
            } else if (l == 7) {
                if (latch) {
                    cnt = al;
                } else {
                    cnt = (cnt & 0xff) | (al * 0x100);
                }
                latch = !latch;
            }
        }

        private void int0bEnt() {
            //byte al = pcmrecmode;
            //if ((al & 1) != 0) { // Recording mode?
            //   record3();
            //} else {
                program_dma();
                clear_fint();
                change_buffer();
                put_fifo_data();
//            }

            // Issue EOI (Probably unnecessary)
            //nax.pc98.OutportB(0, 0x20);
        }

        private int fifoseg = 0;
        private int fifoptr1 = 0;
        private static final int MAXBUF = 18;
        private static final int FIFO_SIZE = 128;
        private final static int PWORKE = 1; // 18;
        private int fifoend1 = FIFO_SIZE * 2;
        private int fifoptr2 = FIFO_SIZE * 2;
        private int fifoend2 = FIFO_SIZE * 4;
        private static final int fifofin = FIFO_SIZE * 2 * MAXBUF;
        private int dma_adr = 0;
        private int dma_bank = 0;
        private int dma_count = 0;
        private static final int dma_data = FIFO_SIZE * 2;
        private static final byte dma_chan = 3;
        private int panl1_ = 0xc008; // or al,al
        private int panl2_ = 0xc008; // or al,al
        private static final int level1_ = 0x007f;
        private static final int level2_ = 0x7f;
        private static final int level3_ = 0x7f;
        int jump1_ = 0;
        int jump2_ = 0;
        /** EMS handle for PCM */
        int phandle = 0xffff;
        /** For saving EMS map information */
        private final byte[] pemsbuf = new byte[32];
        int freq2 = 0x987;
        final EMS ems = new EMS();

        static class Pcm0work {

            /** Extended PCM playback start address/EMS page */
            final int[] pcm0adrs = {0, 0};
            /** Playback subtraction counter * 4 */
            final int[] pcm0cnt = {0, 0};
            /** Frequency */
            final int[] pcm0freq = {0, 0};
            /** right+left pan and data (0/FFFF) */
            final int[] pcm0pan = {0, 0};
            /** Volume */
            final int[] pcm0vol =  {0, 0};
            /** Mute flag */
            boolean mask = false;
        }

        final Pcm0work[] pcm0work = {
                new Pcm0work(), new Pcm0work(), new Pcm0work(), new Pcm0work(),
                new Pcm0work(), new Pcm0work(), new Pcm0work(), new Pcm0work(),
                new Pcm0work(), new Pcm0work(), new Pcm0work(), new Pcm0work(),
                new Pcm0work(), new Pcm0work(), new Pcm0work(), new Pcm0work(),
                new Pcm0work()
        };

        private void program_dma() {
            byte al = 0b0000_0100; // Set DMA mask bit
            //al |= dma_chan;
            //nax.pc98.OutportB(0x15, al); // SingleMaskSet
            //al = 0b0100_1000; // Set DMA mode
            //al |= dma_chan;
            //nax.pc98.OutportB(0x17, al); // ModeReg.

            int ax = fifoseg; // Use DMA dedicated segment
            int eax = (ax << 4) + fifoptr1;

            //progdma_sub:
            //nax.pc98.OutportB(0x19, (byte)eax);// ClearByteF/F
            // Set DMA address
            writeReg(5, eax & 0xff); //nax.pc98.OutportB(dma_adr, (byte)eax);
            writeReg(5, (eax >>> 8) & 0xff); //nax.pc98.OutportB(dma_adr, (byte)(eax >> 8));// DMA adr.

            // Set DMA bank register
            eax >>= 16;
            //nax.pc98.OutportB(dma_bank, (byte)eax); // DMA bank adr.

            // Set DMA counter
            writeReg(7, dma_data & 0xff); //nax.pc98.OutportB(dma_count, (byte)dma_data);
            writeReg(7, (dma_data >>> 8) & 0xff); //nax.pc98.OutportB(dma_count, (byte)(dma_data >> 8)); // DMA count

            al = 0; // Clear DMA mask bit
            al |= dma_chan;
            //nax.pc98.OutportB(0x15, al);// SingleMaskClear
            //nax.pc98.OutportB(0x5f, al);
        }

        private void clear_fint() {
            // WSS specific processing only
            //clear_fintwss:

            // Calls ResetINTFlag but actually does nothing
            //WriteReg(0x0f46, 0xfe);// Write to R2

//            return;
        }

        private void change_buffer() {
            int[] ax = {fifoptr1};
            change_ptr(ax);
            fifoptr1 = ax[0];
            ax[0] += FIFO_SIZE * 2;
            fifoend1 = ax[0];

            ax[0] = fifoptr2;
            change_ptr(ax);
            fifoptr2 = ax[0];
            ax[0] += FIFO_SIZE * 2;
            fifoend2 = ax[0];

        }

        private static void change_ptr(int[] ax) {
            ax[0] += FIFO_SIZE * 2;
            if (ax[0] >= fifofin) {
                ax[0] = 0;
            }
//change_ptr1:
        }

        private void put_fifo_data() {
            //int edxbk = nax.reg.edx;
            //short fsbk = nax.reg.fs;

            int[] bx = {0};
            save_extpcm(bx); // Save EMS map
            int ax = fifoseg; // ES,FS = FIFO segment
            int es = ax;
            int fs = ax;
//sign3:
            ax = 0x8080;
            int cx = (short) FIFO_SIZE;
            int di = fifoptr1;

            // Initialize buffer before FIFO transfer
            do {
                fifoBuf[di++] = (byte) ax;
                fifoBuf[di++] = (byte) (ax >> 8);
                cx--;
            } while (cx > 0);

//segad3:
            ax = (short) 0xc000;
            es = ax;

            // EMS mapping

            cx = 17;
            int si = 0; //ofs:pcm0work
            int dx, bp;
//fifo_map1:
            do {
                if (pcm0work[si].pcm0cnt[0] == 0 && pcm0work[si].pcm0cnt[1] == 0) {
                    si += PWORKE; // To next channel
                    cx--;
                    continue;
                }

                int cxbk = cx;
                bx[0] = pcm0work[si].pcm0adrs[1]; // BX = EMS logical page
                //logger.log(Level.INFO, "%X".formatted(r.bx * 0x4000 + pcm0work[nax.reg.si].pcm0adrs[0]));
//naxad1:
                dx = phandle;
                ax = 0x4400; // EMS mapping
                byte[] ah = {0};
                ems.map(0x00, ah, bx[0], dx);
                byte[] emsMem = ems.getCurrentMapBuf();

                // Write to buffer before FIFO transfer
                ax = pcm0work[si].pcm0pan[0]; // Pan instruction (L)
                panl1_ = ax;
                ax = pcm0work[si].pcm0pan[1]; // Pan instruction (R)
                panl2_ = ax;
                //	jmp	$+2
                bp = pcm0work[si].pcm0adrs[0]; // BP = PCM data address
                cx = pcm0work[si].pcm0freq[0]; // CX = PCM frequency counter
                bx[0] = pcm0work[si].pcm0freq[1]; // BX = Add frequency data
                di = fifoptr1; // FS:DI = Buffer before FIFO transfer
                int edx = pcm0work[si].pcm0cnt[0] +
                        pcm0work[si].pcm0cnt[1] * 0x1_0000;

fifo_skip1_: {
fifo_lop1:
                do {
                    byte al = emsMem[bp];
                    ax = (/* signed */ al * /* signed */ (byte) pcm0work[si & 0xffff].pcm0vol[0]) & 0xffff;
                    ax <<= 2;
                    al = (byte) ((ax >>> 8) & 0xff);
                    ah[0] = (byte) ((ax >>> 8) & 0xff);
                    // debug
                    //nax.reg.ah = nax.reg.al = emsMem[nax.reg.bp];

//panl1:
                    // Kuma: Switching using self-modifying code
                    switch (panl1_ & 0xffff) {
                        case 0xc008: //or al,al
                            al |= al;
                            break;
                        case 0xc030: //xor al,al
                            al ^= al;
                            break;
                    }
//panl2:
                    // Execute pan mask
                    switch (panl2_ & 0xffff) {
                        case 0xc008: //or al,al
                            al |= al;
                            break;
                        case 0xe430: //xor ah,ah
                            ah[0] ^= ah[0];
                            break;
                    }

//fifo_lop2:
                    while (true) {
                        if (pcm0work[si & 0xffff].mask) {
                            di += 2;
                        } else {
                            fifoBuf[di++] = (byte) (fifoBuf[di++] + al);// Add L, R values and store
                            fifoBuf[di++] = (byte) (fifoBuf[di++] + ah[0]);
                        }
                        cx += bx[0];

                        if ((cx & 0x8000) != 0) {
//fifo_freq1:
                            if (di >= fifoend1) {
                                break fifo_lop1; // goto fifo_end1; // Processing when outputting the same value at low frequency
                            }
                            continue; // goto fifo_lop2;
                        }
                        break;
                    }
//fifo_freq2:
                    do {
                        bp++;
                        if (bp >= 16384) { // Switch to next EMS page?
//fifo_freq3:
                            bp = 0;
                            int edxbk1 = edx;
                            int bxbk1 = bx[0];
                            bx[0] = pcm0work[si].pcm0adrs[1]; // BX = EMS logical page
                            bx[0]++;
                            pcm0work[si].pcm0adrs[1] = bx[0];
//naxad2:
                            dx = phandle;
                            ax = 0x4400;// EMS mapping
                            ems.map(0x00, ah, bx[0], dx);
                            emsMem = ems.getCurrentMapBuf();
                            bx[0] = bxbk1;
                            edx = edxbk1;
                        }
//fifo_freq5:
                        edx--;
                        if (edx == 0) {
                            pcm0work[si].pcm0cnt[0] = 0;
                            pcm0work[si].pcm0cnt[1] = 0;
                                break fifo_skip1_; // goto fifo_skip1_;
                        }
//freq2:
                        cx -= freq2 & 0xffff; // (O4CDATA*1.5);// freq2;// O4CDATA;
                    } while ((cx & 0x8000) == 0);
                } while (di < fifoend1); // Loop through the number of FIFO bytes

//fifo_end1:
                pcm0work[si].pcm0cnt[0] = edx & 0xffff;
                pcm0work[si].pcm0cnt[1] = (edx >>> 16) & 0xffff;
                pcm0work[si].pcm0freq[0] = cx;
                pcm0work[si].pcm0adrs[0] = bp;
}
//fifo_skip1_: ↑
                si += PWORKE; // To next channel
                cx = cxbk;
                cx--;

            } while (cx > 0);

            remove_extpcm();

            // DSP processing

//jump1:
            if (jump1_ == 0x3e3e) {
                si = fifoptr2;
                di = fifoptr1;
                cx = FIFO_SIZE;
//jump2:
                switch (jump2_) {
                    case 0:
//test_lop1:
                        do {
                            //	segfs
                            ax = ((fifoBuf[si] & 0xff) + (fifoBuf[si + 1] & 0xff) * 0x100) & 0xffff;
                            si += 2;
//sign1:
                            byte al = (byte) (ax & 0xff);
                            byte ah = (byte) ((ax >>> 8) & 0xff);
                            al -= 0x80;// none
                            ah -= 0x80;
                            dx = 0;
                            dx = ah;
                            ax = (/* signed */ al) & 0xffff;
                            int tmp = ax;
                            ax = dx;
                            dx = tmp;
                            ax = (/* signed */ (byte) (ax & 0xff)) & 0xffff;
                            ax += dx;
//level1:
                            dx = level1_; // 0x007f;
                            int ans = /* signed */ (short) ax * /* signed */ (short) dx;
                            dx = (ans >> 16) & 0xffff;
                            ax = ans & 0xffff;
                            fifoBuf[di] += (byte) (ax >>> 8);
                            fifoBuf[di + 1] -= (byte) (ax >>> 8);
                            di += 2;
                            cx--;
                        } while (cx > 0);
                        break;
                    case 1:
//test_lop2:
                        do {
                            //	segfs
                            ax = ((fifoBuf[si] & 0xff) + (fifoBuf[si + 1] & 0xff) * 0x100) & 0xffff;
                            si += 2;
                            byte al = (byte) ((ax & 0xff) - ((ax >> 8) & 0xff));
//level2:
                            byte ah = (byte) level2_; // 0x7f;
                            ax = (/* signed */ al * /* signed */ ah) & 0xffff;
                            fifoBuf[di + 1] += (byte) (ax >>> 8);
                            fifoBuf[di] -= (byte) (ax >>> 8);
                            di += 2;
                            cx--;
                        } while (cx > 0);
                        break;
                    case 2:
//test_entry3:
                        cx <<= 1;
//test_lop3:
                        do {
                            //	segfs
                            byte al = fifoBuf[si++];
//sign2:
                            al -= 0x80; // none
//level3:
                            byte ah = (byte) (level3_ & 0xff); //0x7f
                            ax = (/* signed */ al * /* signed */ ah) & 0xffff;
                            ax <<= 1;
                            fifoBuf[di] -= (byte) ((ax >>> 8) & 0xff);
                            di++;
                            cx--;
                        } while (cx > 0);
                        break;
                }
            }
        }

        void save_extpcm(int[] bx) {
            bx[0] = ems.getPageMap(); // Save EMS map
        }

        void remove_extpcm() {
            ems.setPageMap((short) 0, pemsbuf);
        }
    }

    static class EMS {

        private int crntEmsHandle = 0;
        private int crntPageMap = 0;
        private int pPageNo = 0;
        private int lPageNo = 0;
        private final Map<Integer, Boolean> useEMSList;
        private final Map<Integer, String> handleName;
        private final Map<Integer, byte[][]> emsBuff;
        private final Map<Integer, int[]> mappedPage;

        EMS() {
            crntEmsHandle = 0;
            useEMSList = new HashMap<>();
            handleName = new HashMap<>();
            emsBuff = new HashMap<>();
            mappedPage = new HashMap<>();
        }

        void getHandleName(byte[] ah, int dx, String[] buf) {
            ah[0] = 0;
            if (handleName.containsKey(dx)) {
                buf[0] = handleName.get(dx);
                return;
            }

            buf[0] = ""; // Kuma: It seems ah becomes 0 even if there is no match
        }

        void setHandleName(byte[] ah, int dx, String emsName2) {
            ah[0] = 0;
            if (!handleName.containsKey(dx))
                handleName.put(dx, emsName2);
            else
                handleName.put(dx, emsName2);
        }

        void allocMemory(byte[] ah, int[] dx, int bx) {
            // Search for unused handle
            int cnt = 0;
            while (cnt < 0x10000) {
                if (!useEMSList.containsKey(crntEmsHandle) || !useEMSList.get(crntEmsHandle)) break;
                crntEmsHandle++;
                crntEmsHandle &= 0xffff;
                cnt++;
            }

            if (cnt == 0x10000) {
                ah[0] = 1;
                return;
            }

            dx[0] = crntEmsHandle;
            if (!useEMSList.containsKey(crntEmsHandle)) useEMSList.put(crntEmsHandle, true);
            else useEMSList.put(crntEmsHandle, true);
            if (!emsBuff.containsKey(crntEmsHandle)) emsBuff.put(crntEmsHandle, null);
            emsBuff.put(crntEmsHandle, new byte[bx][]);
            if (!mappedPage.containsKey(crntEmsHandle)) mappedPage.put(crntEmsHandle, null);
            mappedPage.put(crntEmsHandle, new int[bx]);

            for (int i = 0; i < bx; i++) {
                emsBuff.get(crntEmsHandle)[i] = new byte[16 * 1024]; // alloc 16Kbyte
                for (int j = 0; j < 16 * 1024; j++) emsBuff.get(crntEmsHandle)[i][j] = (byte) 0x80;
                mappedPage.get(crntEmsHandle)[i] = 0xffff; // unmap state
            }
            ah[0] = 0;
        }

        int getPageMap() { // x86Register reg, byte[] pemsbuf)
            //reg.bx = (short) crntPageMap;
            return crntPageMap;
        }

        void map(int al, byte[] ah, int bx, int dx) {
            pPageNo = al; // Physical page number
            lPageNo = bx; // Logical page number

            try {
                // Map
                mappedPage.get(dx)[pPageNo] = lPageNo; // Unmapped state if 0xffff

                ah[0] = 0x00;
                // Normal execution
            } catch (Exception e) {
                ah[0] = (byte) 0x80;
            }
        }

        void setPageMap(short si, byte[] pemsbuf) {
            crntPageMap = pemsbuf[si];// reg.bx;
        }

        byte[] getCurrentMapBuf() {
            return emsBuff.get(crntEmsHandle)[mappedPage.get(crntEmsHandle)[crntPageMap]];
        }

        public byte[] getEmsArray(int stPage, int endPage) {
            try {
                ByteArrayOutputStream lst = new ByteArrayOutputStream();
                for (int i = stPage; i < endPage; i++) {
                    lst.write(emsBuff.get(crntEmsHandle)[i]);
                }
                return lst.toByteArray();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
