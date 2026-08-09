/*
 * https://web.archive.org/web/20200810210111/https://vstcpp.wpblog.jp/?p=1797
 */

package mdsound.fmvgen.effect;


/**
 * A chorus flanger is an effect that adds thickness to the sound
 * by mixing a pitch-shifted sound with the input signal.
 * There is no clear difference between a chorus and a flanger,
 * and they are called different names depending on the size of the pitch shift.
 * A chorus has little pitch shift and just adds thickness to the input signal,
 * while a flanger has a larger pitch shift and is like adding a jet sound to the input signal.
 *
 * @author twitter:@vstcpp
 * @see "https://web.archive.org/web/20200810210111/https://vstcpp.wpblog.jp/?p=1797"
 */
public class Chorus {

    private final float clock;
    private final int maxCh;
    private ChInfo[] chInfo = null;
    private int currentCh = 0;

    static class ChInfo {

        boolean sw;

        /** Chorus effect level. Between 0.0 and 1.0 */
        float mix = 0.3f;
        /** Chorus fluctuation interval. 0Hz to 16Hz */
        float rate = 3.0f;
        /** Depth of chorus fluctuation. Approximately 5.0 to 200.0 samples */
        float depth = 10.0f;
        /** Chorus feedback amount. Between 0.0 and 1.0 */
        float feedback = 0.3f;

        /**
         * Ring Buffer
         * @see "https://vstcpp.wpblog.jp/?p=1505"
         */
        final RingBuffer ringBufL;
        final RingBuffer ringBufR;

        /**
         * Set the delay time by converting it into a number of samples
         * The sample position to be read will move according to the depth,
         * so make sure that the interval does not become 0 or less when it moves.
         * For now, let’s take about 1000 samples.
         * (Interval is a ring buffer. See https://vstcpp.wpblog.jp/?p=1505)
         */
        final int delaySample;

        float theta;
//        public float speed;

        ChInfo(int clock) {
            delaySample = 10;
            theta = 0; // The angle θ of the sine function to fluctuate the delay reading position. The initial value is 0.

            sw = false;
            ringBufL = new RingBuffer(clock, 0.02f);
            ringBufR = new RingBuffer(clock, 0.02f);
            ringBufL.setInterval(delaySample);
            ringBufR.setInterval(delaySample);
        }
    }

    public Chorus(int clock, int maxCh) {
        this.clock = (float) clock;
        this.maxCh = maxCh;
        init();
    }

    private void init() {
        chInfo = new ChInfo[maxCh];
        for (int i = 0; i < chInfo.length; i++) {
            chInfo[i] = new ChInfo((int) clock);
        }
    }

    /**
     * Linear Interpolation Function
     * Linearly interpolate v1 and v2 with the ratio t, where t is in the range 0.0 to 1.0.
     * When t is 0.0, it is the value of v1, and when t is 1.0, it is the value of v2.
     */
    private static float lerp(float v1, float v2, float t) {
        return (1.0f - t) * v1 + t * v2;
    }

    public void mix(int ch, int[] inL, int[] inR) {
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

        ChInfo ci = chInfo[ch];
        float finL = inL[0] / 21474.83647f;
        float finR = inR[0] / 21474.83647f;
        float speed = (2.0f * 3.14159265f * ci.rate) / clock; // The speed of fluctuation. It is the same as the angular velocity ω.

        // inL[], inR[], outL[], and outR[] are the input and output signal buffers (left and right) respectively.
        // wavelenght is the buffer size, and the sampling frequency is 44100Hz.

        // Apply chorus to the input signal
        // Add the angular velocity to the angle θ
        ci.theta += speed;

        // Calculate the amount to shake the read position
        // Multiply the result of the sin() function by depth
        float a = (float) (Math.sin(ci.theta) * ci.depth);

        // Get integer values before and after the read position is swayed (for linear interpolation later)
        int p1 = (int) a;
        int p2 = (int) (a + 1);

        // The value of the read position is calculated by linear interpolation from the integer values before and after
        float lerpL1 = lerp(ci.ringBufL.read(p1), ci.ringBufL.read(p2), a - (float) p1);
        float lerpR1 = lerp(ci.ringBufR.read(p1), ci.ringBufR.read(p2), a - (float) p1);

        // Mix the delayed signal with the input signal
        float tmpL = (1.0f - ci.mix) * finL + ci.mix * lerpL1;
        float tmpR = (1.0f - ci.mix) * finR + ci.mix * lerpR1;

        // Write the input signal and feedback signal to a ring buffer as a delayed signal.
        ci.ringBufL.write((1.0f - ci.feedback) * finL + ci.feedback * tmpL);
        ci.ringBufR.write((1.0f - ci.feedback) * finR + ci.feedback * tmpR);

        // Update the state of the ring buffer
        ci.ringBufL.update();
        ci.ringBufR.update();

        // Write to output signal
        finL = tmpL;
        finR = tmpR;

        inL[0] = (int) (finL * 21474.83647f);
        inR[0] = (int) (finR * 21474.83647f);
    }

    public void setReg(int adr, byte data) {
        if (adr == 0) {
            currentCh = Math.clamp(data & 0x3f, 0, 38);
            if ((data & 0x80) != 0)
                init();
        } else if (adr == 1) {
            chInfo[currentCh].sw = ((data & 0x80) != 0);
            chInfo[currentCh].mix = (data & 0x7f) / 127.0f;
        } else if (adr == 2) {
            chInfo[currentCh].rate = 16.0f * (data & 0x7f) / 127.0f;
        } else if (adr == 3) {
            chInfo[currentCh].depth = 195.0f * (data & 0x7f) / 127.0f + 5.0f;
        } else if (adr == 4) {
            chInfo[currentCh].feedback = (data & 0x7f) / 127.0f;
        }
    }
}
