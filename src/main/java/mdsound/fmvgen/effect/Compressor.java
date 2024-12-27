
package mdsound.fmvgen.effect;

/**
 * Filter Class
 *
 * @see "https://vstcpp.wpblog.jp/?p=1939"
 */
public class Compressor {

    private int sampleRate = 44100;

    private int currentCh = 0;
    private final int maxCh;
    private ChInfo[] chInfo = null;
    private final float[] fBuf = new float[2];

    private static class ChInfo {

        public boolean sw;

        // Effector parameters

        /** The sound pressure at which compression begins. Approximately 0.1 to 1.0 */
        public float threshold;
        /** Compression ratio: 2.0 to 10.0 */
        public float ratio;
        /** Final volume. Approximately 1.0 to 3.0 */
        public float volume;

        // Internal variables

        /**
         * A low-pass filter used to detect sound pressure
         * @see "https://vstcpp.wpblog.jp/?page_id=728"
         */
        public Filter envfilterL, envfilterR;
        // Low-pass filter to avoid sudden volume changes
        public Filter gainfilterL, gainfilterR;

        public float envFreq;
        public float envQ;
        public float gainFreq;
        public float gainQ;

        ChInfo() {
            this.sw = false;

            this.threshold = 0.3f;
            this.ratio = 2.0f;
            this.volume = 2.0f;

            this.envFreq = 30.0f;
            this.envQ = 1.0f;
            this.gainFreq = 5.0f;
            this.gainQ = 1.0f;
            this.envfilterL = new Filter();
            this.envfilterR = new Filter(); // A low-pass filter used to detect sound pressure
            this.gainfilterL = new Filter();
            this.gainfilterR = new Filter(); // Low-pass filter to avoid sudden volume changes
        }

        public void setReg(int adr, byte data, int sampleRate) {
            if (adr == 1) {
                this.sw = ((data & 0x80) != 0);
                this.volume = (data & 0x7f) / (127.0f / 4.0f);
            } else if (adr == 2) {
                this.threshold = Math.max(data / 255.0f, 0.1f);
            } else if (adr == 3) {
                this.ratio = Math.max(data / (255.0f / 10.0f), 1.0f);
            } else if (adr == 4) {
                this.envFreq = data / (255.0f / 80.0f);
                this.envfilterL.lowPass(this.envFreq, this.envQ, sampleRate);
                this.envfilterR.lowPass(this.envFreq, this.envQ, sampleRate);
            } else if (adr == 5) {
                this.envQ = Filter.qTable[data];
                this.envfilterL.lowPass(this.envFreq, this.envQ, sampleRate);
                this.envfilterR.lowPass(this.envFreq, this.envQ, sampleRate);
            } else if (adr == 6) {
                this.gainFreq = data / (255.0f / 80.0f);
                this.gainfilterL.lowPass(this.gainFreq, this.gainQ, sampleRate);
                this.gainfilterR.lowPass(this.gainFreq, this.gainQ, sampleRate);
            } else if (adr == 7) {
                this.gainQ = Filter.qTable[data];
                this.gainfilterL.lowPass(this.gainFreq, this.gainQ, sampleRate);
                this.gainfilterR.lowPass(this.gainFreq, this.gainQ, sampleRate);
            }
        }

        public void setLowPass(float envFreq, float envQ, float gainFreq, float gainQ, int sampleRate) {
            // The higher the cutoff frequency, the more sensitive it is to changes in sound pressure.
            // A good guideline is around 10 to 50 Hz.
            this.envfilterL.lowPass(envFreq, envQ, sampleRate);
            this.envfilterR.lowPass(envFreq, envQ, sampleRate);
            // The higher the cutoff frequency, the more rapid the volume change.
            // A good guideline is around 5 to 50 Hz.
            this.gainfilterL.lowPass(gainFreq, gainQ, sampleRate);
            this.gainfilterR.lowPass(gainFreq, gainQ, sampleRate);
        }
    }

    public Compressor(int sampleRate, int maxCh) {
        this.sampleRate = sampleRate;
        this.maxCh = maxCh;
        init();
    }

    public void init() {
        currentCh = 0;
        chInfo = new ChInfo[maxCh];
        for (int i = 0; i < chInfo.length; i++) {
            chInfo[i] = new ChInfo();
            setLowPass(i, 30.0f, 1.0f, 5.0f, 1.0f);
        }
    }

    public void mix(int ch, int[] inL, int[] inR) {
        mix(ch, inL, inR, 1);
    }

    public void mix(int ch, int[] inL, int[] inR, int waveLength) {
        if (ch < 0) return;
        if (ch >= maxCh) return;
        if (chInfo == null) return;
        if (chInfo[ch] == null) return;
        if (!chInfo[ch].sw) return;

        fBuf[0] = inL[0] / 21474.83647f;
        fBuf[1] = inR[0] / 21474.83647f;

        // inL[], inR[], outL[], and outR[] are the input and output signal buffers (left and right) respectively.
        // wavelenght is the buffer size, and the sampling frequency is 44100Hz.

        // Applying effects to the input signal
        // The absolute value of the input signal is passed through a low-pass filter to detect the sound pressure.
        float tmpL = chInfo[ch].envfilterL.process(Math.abs(fBuf[0]));
        float tmpR = chInfo[ch].envfilterR.process(Math.abs(fBuf[1]));

        // Adjusting the volume (gain) based on sound pressure (left)
        float gainL = 1.0f;

        if (tmpL > chInfo[ch].threshold) {
            // The threshold has been exceeded so the volume (gain) is adjusted (compressed)
            gainL = chInfo[ch].threshold + (tmpL - chInfo[ch].threshold) / chInfo[ch].ratio;
        }
        // Pass the signal through a low-pass filter to prevent sudden changes in volume (gain)
        gainL = chInfo[ch].gainfilterL.process(gainL);

        // The volume (gain) of the right side is adjusted based on the sound pressure just like the left side.
        float gainR = 1.0f;
        if (tmpR > chInfo[ch].threshold) {
            gainR = chInfo[ch].threshold + (tmpR - chInfo[ch].threshold) / chInfo[ch].ratio;
        }
        gainR = chInfo[ch].gainfilterR.process(gainR);

        // The volume (gain) is applied to the input signal, and then the final volume is adjusted before being output.
        fBuf[0] = chInfo[ch].volume * gainL * fBuf[0];
        fBuf[1] = chInfo[ch].volume * gainR * fBuf[1];
        inL[0] = (int) (fBuf[0] * 21474.83647f);
        inR[0] = (int) (fBuf[1] * 21474.83647f);
    }

    /** Set the low pass filter */
    private void setLowPass(int ch, float envFreq, float envQ, float gainFreq, float gainQ) {
        chInfo[ch].setLowPass(envFreq, envQ, gainFreq, gainQ, sampleRate);
    }

    public void setReg(int adr, byte data) {
        if (adr == 0) {
            currentCh = Math.max(Math.min(data & 0x3f, 38), 0);
            if ((data & 0x80) != 0)
                init();
        } else {
            chInfo[currentCh].setReg(adr, data, sampleRate);
        }
    }
}
