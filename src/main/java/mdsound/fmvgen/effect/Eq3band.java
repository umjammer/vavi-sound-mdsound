/*
 * https://web.archive.org/web/20200702131730/https://vstcpp.wpblog.jp/?p=1417
 */

package mdsound.fmvgen.effect;


/**
 * A 3-band equalizer is an effector that amplifies
 * the low, mid, and high frequencies of the input audio.
 *
 * @author twitter:@vstcpp
 * @see "https://web.archive.org/web/20200702131730/https://vstcpp.wpblog.jp/?p=1417"
 */
public class Eq3band {
    private float fL, fR;
    private int sampleRate = 44100;

    // Effector parameters
    private boolean lowSw = false;
    private float lowFreq = 400.0f; // Low frequency range. Approximately 50Hz to 1kHz
    private float lowGain = 2.0f; // Low frequency gain (amplification value). Approximately -15 to 15 dB.
    private float lowQ = (float) (1.0f / Math.sqrt(2.0f));

    private boolean midSw = false;
    private float midfreq = 1000.0f; // Mid-range frequency. Approximately 500Hz to 4kHz
    private float midGain = -4.0f; // Mid-range gain (amplification value). Approximately -15 to 15 dB.
    private float midQ = (float) (1.0f / Math.sqrt(2.0f));

    private boolean highSw = false;
    private float highFreq = 4000.0f; // High-pitched frequency: 1kHz to 12kHz
    private float highGain = 4.0f; // Treble gain (amplification value). Approximately -15 to 15 dB.
    private float highQ = (float) (1.0f / Math.sqrt(2.0f));

    // The default value of the parameter is
    // low
    //  freq:126
    //  gain:141
    //  Q:67
    // mid
    //  freq:162
    //  gain:102
    //  Q:67
    // high
    //  freq:192
    //  gain:154
    //  Q:67

    // Internal variables
    private final Filter lowL = new Filter();
    private final Filter lowR = new Filter();
    private final Filter midL = new Filter();
    private final Filter midR = new Filter();
    private final Filter highL = new Filter();
    private final Filter highR = new Filter(); // Filter class (https://vstcpp.wpblog.jp/?page_id=728)

    public Eq3band(int sampleRate /* = 44100 */) {
        this.sampleRate = sampleRate;
        updateParam();
    }

    public void mix(int[] buffer, int nsamples) {
        for (int i = 0; i < nsamples; i++) {
            fL = buffer[i * 2 + 0] / Filter.convInt;
            fR = buffer[i * 2 + 1] / Filter.convInt;


            // inL[], inR[], outL[], and outR[] are the input and output signal buffers (left and right) respectively.
            // wavelenght is the buffer size, and the sampling frequency is 44100Hz.
            // Applying effects to the input signal
            // Filtering the input signal
            if (lowSw) {
                fL = lowL.process(fL);
                fR = lowR.process(fR);
            }
            if (midSw) {
                fL = midL.process(fL);
                fR = midR.process(fR);
            }
            if (highSw) {
                fL = highL.process(fL);
                fR = highR.process(fR);
            }

            buffer[i * 2 + 0] = (int) (fL * Filter.convInt);
            buffer[i * 2 + 1] = (int) (fR * Filter.convInt);
        }
    }

    public void setReg(int adr, byte data) {
        switch (adr & 0xf) {
        case 0:
            lowSw = data != 0;
            break;
        case 1:
            lowFreq = Filter.freqTable[data];
            break;
        case 2:
            lowGain = Filter.gainTable[data];
            break;
        case 3:
            lowQ = Filter.qTable[data];
            break;

        case 4:
            midSw = data != 0;
            break;
        case 5:
            midfreq = Filter.freqTable[data];
            break;
        case 6:
            midGain = Filter.gainTable[data];
            break;
        case 7:
            midQ = Filter.qTable[data];
            break;

        case 8:
            highSw = data != 0;
            break;
        case 9:
            highFreq = Filter.freqTable[data];
            break;
        case 10:
            highGain = Filter.gainTable[data];
            break;
        case 11:
            highQ = Filter.qTable[data];
            break;
        }

        updateParam();
    }

    private void updateParam() {
        // Low-shelf filter settings (left and right)
        lowL.lowShelf(lowFreq, lowQ, lowGain, sampleRate);
        lowR.lowShelf(lowFreq, lowQ, lowGain, sampleRate);
        // Mid-range boost (peaking) filter setting (left and right)
        midL.peaking(midfreq, midQ, midGain, sampleRate);
        midL.peaking(midfreq, midQ, midGain, sampleRate);
        // Low shelf filter setting (left and right)
        highL.highShelf(highFreq, highQ, highGain, sampleRate);
        highR.highShelf(highFreq, highQ, highGain, sampleRate);
    }
}
