/*
 * https://web.archive.org/web/20210116062027/https://vstcpp.wpblog.jp/?p=1426
 */

package mdsound.fmvgen.effect;


/**
 * Overdrive distortion is an effect that distorts the sound by amplifying the input signal
 * after cutting out unnecessary low-frequency sounds, and clipping anything that exceeds
 * the maximum amplitude. Overdrive and distortion are generally distinguished only by the
 * amount of amplification, so they will be treated together here. The descriptions of fuzz,
 * overdrive, and distortion seem to vary depending on the book or website, but here we will
 * define overdrive distortion as an input signal that is filtered, amplified, and clipped.
 *
 * @author twitter:@vstcpp
 * @see "https://web.archive.org/web/20210116062027/https://vstcpp.wpblog.jp/?p=1426"
 */
public class Distortion {

    // Effector parameters
    private final int clock;
    private final int maxCh;
    private ChInfo[] chInfo = null;

    private final float[] fbuf = new float[] {
        0f, 0f
    };

    private int currentCh = 0;

    private static class ChInfo {
        public boolean sw = false;

        public Filter highpassL = new Filter();

        public Filter highpassR = new Filter();
        /** Amplification amount: 10 to 300 (20 dB to 50 dB in dB conversion) */
        public float gain = 300.0f;
        /** The volume of the output signal, ranging from 0.0 to 1.0. */
        public float volume = 0.1f;
    }

    public Distortion(int clock, int maxCh) {
        this.clock = clock;
        this.maxCh = maxCh;
        init();
    }

    public void init() {
        chInfo = new ChInfo[maxCh];
        for (int i = 0; i < chInfo.length; i++) {
            chInfo[i] = new ChInfo();
            chInfo[i].sw = false;

            // Internal variables
            // Filter setting (left and right) to pass only high frequencies (cut low frequencies)
            // The recommended frequency range for cutting is around 20Hz to 300Hz.
            // The larger the amplification amount, the larger the cutoff frequency should be.
            chInfo[i].highpassL = new Filter();
            chInfo[i].highpassL.highPass(200.0f, (float) (1.0f / Math.sqrt(2.0f)), clock);
            chInfo[i].highpassR = new Filter();
            chInfo[i].highpassR.highPass(200.0f, (float) (1.0f / Math.sqrt(2.0f)), clock);
            chInfo[i].gain = 300.0f;
            chInfo[i].volume = 0.1f;
        }
    }

    public void mix(int ch, int[] inL, int[] inR) {
        mix(ch, inL, inR, 1);
    }

    public void mix(int ch, int[] inL, int[] inR, int waveLength) {
        if (ch < 0)
            return;
        if (ch >= maxCh)
            return;
        if (chInfo == null)
            return;
        if (chInfo[ch] == null)
            return;
        if (!chInfo[ch].sw)
            return;

        fbuf[0] = inL[0] / 21474.83647f;
        fbuf[1] = inR[0] / 21474.83647f;

        // inL[], inR[], outL[], and outR[] are the input and output signal buffers (left and right) respectively.
        // wavelenght is the buffer size, and the sampling frequency is 44100Hz.

        // Applying effects to the input signal
        for (int i = 0; i < waveLength * 2; i += 2) {
            // Applying a filter to the input signal
            float tmpL = chInfo[ch].highpassL.process(fbuf[i + 0]);
            // Amplify the input signal by applying gain
            tmpL = chInfo[ch].gain * tmpL;

            // Clipping occurs when the amplitude exceeds the maximum value (here, -1.0 to 1.0).
            if (tmpL > 1.0) {
                tmpL = 1.0f;
            }
            if (tmpL < -1.0) {
                tmpL = -1.0f;
            }

            // The right input signal is processed in the same way.
            float tmpR = chInfo[ch].highpassR.process(fbuf[i + 1]);
            tmpR = chInfo[ch].gain * tmpR;
            if (tmpR > 1.0) {
                tmpR = 1.0f;
            }
            if (tmpR < -1.0) {
                tmpR = -1.0f;
            }

            // Filtering the input signal
            fbuf[i + 0] = chInfo[ch].volume * tmpL;
            fbuf[i + 1] = chInfo[ch].volume * tmpR;
        }

        inL[0] = (int) (fbuf[0] * 21474.83647f);
        inR[0] = (int) (fbuf[1] * 21474.83647f);
    }

    public void setReg(int adr, byte data) {
        if (adr == 0) {
            currentCh = Math.clamp(data & 0x3f, 0, 38);
            if ((data & 0x80) != 0)
                init();
        } else if (adr == 1) {
            chInfo[currentCh].sw = ((data & 0x80) != 0);
            chInfo[currentCh].volume = (data & 0x7f) / 320.0f;
        } else if (adr == 2) {
            chInfo[currentCh].gain = (1000.0f - 20.0f) * (data & 0x7f) / 128.0f + 20.0f;
        } else if (adr == 3) {
            float f = 1000.0f * (data & 0x7f) / 256.0f;
            chInfo[currentCh].highpassL.highPass(f, (float) (1.0f / Math.sqrt(2.0f)), clock);
            chInfo[currentCh].highpassR.highPass(f, (float) (1.0f / Math.sqrt(2.0f)), clock);
        }
    }
}
