package mdsound.chips;

// PC-9801-118
// PCM (Windows Sound System)
public class Cs4231 {

    int indexAddress;
    int indexData;
    int status;
    int PIOData;
    int[] reg = new int[32];
    int dmaInt;
    public int renderingFreq;
    public short[] sound = new short[2];
    short[][] sound2 = {
            new short[2], new short[2], new short[2], new short[2], new short[2],
            new short[2], new short[2], new short[2], new short[2], new short[2]
    };
    static final int[] xtal = {24_576_000, 16_934_400};
    static final int[] divTbl = {3072, 1536, 896, 768, 448, 384, 512, 2560};
    public DMA dma = new DMA();
    double step = 0;
    double counter = 0;

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

    public int write(int port, int adr, int data) {
        if (port == 0) {
            switch (adr) {
                case 0:
                    this.indexAddress = data;
                    break;
                case 1:
                    this.indexData = data;
                    this.reg[this.indexAddress & 0x1f] = this.indexData;
                    break;
                case 2:
                    //status = dat;
                    resetIntFlag();
                    break;
                case 3:
                    this.PIOData = data;
                    break;
                case 4:
                    this.dmaInt = data;
                    break;
            }
        } else {
            switch (adr) {
                case 0x5:
                    this.dma.writeReg(5, data);
                    break;
                case 0x7:
                    this.dma.writeReg(7, data);
                    break;
            }

        }

        return 0;
    }

    public int readReg(int adr) {
        switch (adr) {
            case 0:
                return this.indexAddress;
            case 1:
                return this.reg[this.indexAddress & 0x1f];
            case 2:
                return this.status;
            case 3:
                return this.PIOData;
            case 4:
                return this.dmaInt;
        }
        return 0;
    }

    public void setFifoBuf(byte[] buf) {
        for (int i = 0; i < buf.length; i++) {
            dma.fifoBuf = buf;
        }
    }

    public void setInt0bEnt(byte ChipID, Runnable callback) {
        dma.int0bEnt = callback;
    }

    private void exec(short rcnt) {
        short dat0 = (short) ((this.dma.getData() - 0x80) * 380);
        short dat1 = (short) ((this.dma.getData() - 0x80) * 380);
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
        public byte[] fifoBuf;
        public Runnable int0bEnt;
        private boolean latch = true;

        public DMA() {
            this.ptr = 0;
            this.cnt = 0;
            //this.fifoBuf = fifoBuf;
            //this.int0bEnt = int0bEnt;
        }

        public byte getData() {
            if (fifoBuf == null) return (byte) 0x80;

            byte dat = fifoBuf[ptr];
//            if (dat == null) return (byte) 0x80;

            ptr++;
            cnt--;

            if (ptr == fifoBuf.length || cnt <= 0) {
                int0bEnt.run();
                if (ptr == fifoBuf.length) {
                    ptr = 0;
                }
            }

            return dat;
        }


        public void writeReg(int l, int al) {
            if (l == 5) {
                if (latch) {
                    ptr = al;
                } else {
                    ptr = ptr | (al * 0x100);
                }
                latch = !latch;
            } else if (l == 7) {
                if (latch) {
                    cnt = al;
                } else {
                    cnt = cnt | (al * 0x100);
                }
                latch = !latch;
            }
        }
    }
}
