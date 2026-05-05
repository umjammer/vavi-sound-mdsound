package mdsound.chips;

public class Gigatron {

    private static class Channel {

        private short osc;
        private short key;
        private byte wavX;
        private byte wavA;
    }

    private final Channel[] ch = {
            new Channel(), new Channel(), new Channel(), new Channel()
    };
    private byte[] soundTable = new byte[256];
    private byte samp = 3;
    private double scanlineCounter = 0;

    private static final double scanlines = 521.0;
    private static final double vSync = 59.98;
    private double bClock = 521.0 * 59.98; // scanlines * vSync
    private double audioSampleRate = 44100;
    private byte channelMask = 0x3;

    public void reset() {
        stop();
        resetSample();
    }

    public int start(int sampleRate, int clock) {
        this.audioSampleRate = sampleRate;
        this.bClock = clock * 2;

        reset();

        return sampleRate;
    }

    public void stop() {
        for (Channel ch : this.ch) {
            ch.osc = 0;
            ch.key = 0;
            ch.wavX = 0;
            ch.wavA = 0;
        }
    }

    public void update(int[][] outputs, int samples) {
        // Synthesis
        for (int p = 0; p < samples; p++) {

            // Update scanlineCounter
            this.scanlineCounter += this.audioSampleRate / this.bClock;

            // Executed every 4 scanlines
            while (this.scanlineCounter >= 4.0) {
                this.samp = 3;

                // channel update
                for (int n = 0; n < 4; n++) {

                    int c = n & this.channelMask;// ? from dev.asm.py

                    this.ch[c].osc += this.ch[c].key;
                    byte i = (byte) ((this.ch[c].osc >> 7) & 0xfc);
                    i ^= this.ch[c].wavX;
                    i = (byte) (this.soundTable[i] + this.ch[c].wavA);
                    i = (byte) ((i & 128) != 0 ? 63 : (i & 63));
                    this.samp += i;
                }

                // Only the upper 4 bits are output
                this.samp &= 0xf0;

                this.scanlineCounter -= 4.0;
            }

            outputs[0][p] = this.samp << 8;
            outputs[1][p] = this.samp << 8;
        }
    }

    public int write(int port, int adr, int data) {
        short ad = (short) adr;
        byte dat = (byte) data;

        if (ad == 0x21) {
            this.channelMask = (byte) (dat & 0x7);
            return 0;
        }

        int hi = ad & 0xff00;
        int lo = ad & 0xff;

        if (hi == 0x0700) {
            this.soundTable[lo] = dat;
            return 0;
        }

        if (lo < 250) return 0;
        if (hi < 0x100) return 0;
        if (hi > 0x400) return 0;

        Channel c = this.ch[(hi >> 8) - 1];
        switch (lo) {
            case 250:
                c.wavA = dat;
                break;
            case 251:
                c.wavX = dat;
                break;
            case 252:
                c.key = (short) ((c.key & 0xff80) | (dat & 0x7f));
                break;
            case 253:
                c.key = (short) ((c.key & 0x007f) | (dat << 7));
                break;
            case 254:
                c.osc = (short) ((c.osc & 0xff80) | (dat & 0x7f));
                break;
            case 255:
                c.osc = (short) ((c.osc & 0x007f) | (dat << 7));
                break;
        }

        return 0;
    }

    private void resetSample() {
        this.soundTable = new byte[256];
        int r = (int) System.currentTimeMillis();
        for (int i = 0; i < 64; i++) {
            //noise
            r += r * 56465321 + 456156321;
            this.soundTable[i * 4 + 0] = (byte) (r & 63);
            //Triangle
            this.soundTable[i * 4 + 1] = (byte) (i < 32 ? 2 * i : (127 - 2 * i));
            //Pulse
            this.soundTable[i * 4 + 2] = (byte) (i < 32 ? 0 : 63);
            //Sawtooth
            this.soundTable[i * 4 + 3] = (byte) i;
        }
    }
}
