
package mdsound.fmvgen.effect;

class Filter {

    public final static float convInt = 21474.83647f;
    public static float[] freqTable;
    public static float[] gainTable;
    public static float[] qTable;

    // Filter Coefficients
    private float a0, a1, a2, b0, b1, b2;

    // buffer
    private float out1, out2;
    private float in1, in2;

    public Filter() {
        // Initialize member variables
        a0 = 1.0f; // If the value is not 0, an error will occur during division.
        a1 = 0.0f;
        a2 = 0.0f;
        b0 = 1.0f;
        b1 = 0.0f;
        b2 = 0.0f;

        in1 = 0.0f;
        in2 = 0.0f;

        out1 = 0.0f;
        out2 = 0.0f;
    }

    /**
     * A function that applies a filter to an input signal
     */
    public float process(float in) {
        // Apply a filter to the input signal and store it in the output signal variable.
        float out = b0 / a0 * in + b1 / a0 * in1 + b2 / a0 * in2 - a1 / a0 * out1 - a2 / a0 * out2;

        in2 = in1; // Update the previous two input signals
        in1 = in; // Update the previous input signal

        out2 = out1; // Update the previous two output signals
        out1 = out; // Update the previous output signal

        // Return the output signal
        return out;
    }

    public void lowPass(float freq, float q, float sampleRate) {
        // Obtain intermediate values for use in filter coefficient calculations.
        float omega = 2.0f * (float) Math.PI * freq / sampleRate;
        float alpha = (float) (Math.sin(omega) / (2.0f * q));

        // Find the filter coefficients.
        a0 = 1.0f + alpha;
        a1 = (float) (-2.0f * Math.cos(omega));
        a2 = 1.0f - alpha;
        b0 = (float) ((1.0f - Math.cos(omega)) / 2.0f);
        b1 = (float) (1.0f - Math.cos(omega));
        b2 = (float) ((1.0f - Math.cos(omega)) / 2.0f);
    }

    public void highPass(float freq, float q, float sampleRate) {
        // Obtain intermediate values for use in filter coefficient calculations.
        float omega = 2.0f * (float) Math.PI * freq / sampleRate;
        float alpha = (float) (Math.sin(omega) / (2.0f * q));

        // Find the filter coefficients.
        a0 = 1.0f + alpha;
        a1 = (float) (-2.0f * Math.cos(omega));
        a2 = 1.0f - alpha;
        b0 = (float) ((1.0f + Math.cos(omega)) / 2.0f);
        b1 = (float) (-(1.0f + Math.cos(omega)));
        b2 = (float) ((1.0f + Math.cos(omega)) / 2.0f);
    }

    public void bandPass(float freq, float bw, float sampleRate) {
        // Obtain intermediate values for use in filter coefficient calculations.
        float omega = 2.0f * (float) Math.PI * freq / sampleRate;
        float alpha = (float) (Math.sin(omega) * Math.sinh(Math.log(2.0f) / 2.0 * bw * omega / Math.sin(omega)));

        // Find the filter coefficients.
        a0 = 1.0f + alpha;
        a1 = (float) (-2.0f * Math.cos(omega));
        a2 = 1.0f - alpha;
        b0 = alpha;
        b1 = 0.0f;
        b2 = -alpha;
    }

    public void notch(float freq, float bw, float sampleRate) {
        // Obtain intermediate values for use in filter coefficient calculations.
        float omega = 2.0f * (float) Math.PI * freq / sampleRate;
        float alpha = (float) (Math.sin(omega) * Math.sinh(Math.log(2.0f) / 2.0 * bw * omega / Math.sin(omega)));

        // Find the filter coefficients.
        a0 = 1.0f + alpha;
        a1 = (float) (-2.0f * Math.cos(omega));
        a2 = 1.0f - alpha;
        b0 = 1.0f;
        b1 = (float) (-2.0f * Math.cos(omega));
        b2 = 1.0f;
    }

    public void lowShelf(float freq, float q, float gain, float sampleRate) {
        // Obtain intermediate values for use in filter coefficient calculations.
        float omega = 2.0f * 3.14159265f * freq / sampleRate;
        float alpha = (float) (Math.sin(omega) / (2.0f * q));
        float A = (float) (Math.pow(10.0f, (gain / 40.0f)));
        float beta = (float) (Math.sqrt(A) / q);

        // Find the filter coefficients.
        a0 = (float) ((A + 1.0f) + (A - 1.0f) * Math.cos(omega) + beta * Math.sin(omega));
        a1 = (float) (-2.0f * ((A - 1.0f) + (A + 1.0f) * Math.cos(omega)));
        a2 = (float) ((A + 1.0f) + (A - 1.0f) * Math.cos(omega) - beta * Math.sin(omega));
        b0 = (float) (A * ((A + 1.0f) - (A - 1.0f) * Math.cos(omega) + beta * Math.sin(omega)));
        b1 = (float) (2.0f * A * ((A - 1.0f) - (A + 1.0f) * Math.cos(omega)));
        b2 = (float) (A * ((A + 1.0f) - (A - 1.0f) * Math.cos(omega) - beta * Math.sin(omega)));
    }

    public void highShelf(float freq, float q, float gain, float sampleRate) {
        // Obtain intermediate values for use in filter coefficient calculations.
        float omega = 2.0f * 3.14159265f * freq / sampleRate;
        float alpha = (float) (Math.sin(omega) / (2.0f * q));
        float A = (float) (Math.pow(10.0f, (gain / 40.0f)));
        float beta = (float) (Math.sqrt(A) / q);

        // Find the filter coefficients.
        a0 = (float) ((A + 1.0f) - (A - 1.0f) * Math.cos(omega) + beta * Math.sin(omega));
        a1 = (float) (2.0f * ((A - 1.0f) - (A + 1.0f) * Math.cos(omega)));
        a2 = (float) ((A + 1.0f) - (A - 1.0f) * Math.cos(omega) - beta * Math.sin(omega));
        b0 = (float) (A * ((A + 1.0f) + (A - 1.0f) * Math.cos(omega) + beta * Math.sin(omega)));
        b1 = (float) (-2.0f * A * ((A - 1.0f) + (A + 1.0f) * Math.cos(omega)));
        b2 = (float) (A * ((A + 1.0f) + (A - 1.0f) * Math.cos(omega) - beta * Math.sin(omega)));
    }

    public void peaking(float freq, float bw, float gain, float sampleRate) {
        // Obtain intermediate values for use in filter coefficient calculations.
        float omega = 2.0f * 3.14159265f * freq / sampleRate;
        float alpha = (float) (Math.sin(omega) * Math.sinh(Math.log(2.0f) / 2.0 * bw * omega / Math.sin(omega)));
        float A = (float) (Math.pow(10.0f, (gain / 40.0f)));

        // Find the filter coefficients.
        a0 = 1.0f + alpha / A;
        a1 = (float) (-2.0f * Math.cos(omega));
        a2 = 1.0f - alpha / A;
        b0 = 1.0f + alpha * A;
        b1 = (float) (-2.0f * Math.cos(omega));
        b2 = 1.0f - alpha * A;
    }

    public void allPass(float freq, float q, float sampleRate) {
        // Obtain intermediate values for use in filter coefficient calculations.
        float omega = 2.0f * 3.14159265f * freq / sampleRate;
        float alpha = (float) (Math.sin(omega) / (2.0f * q));

        // Find the filter coefficients.
        a0 = 1.0f + alpha;
        a1 = (float) (-2.0f * Math.cos(omega));
        a2 = 1.0f - alpha;
        b0 = 1.0f - alpha;
        b1 = (float) (-2.0f * Math.cos(omega));
        b2 = 1.0f + alpha;
    }

    static {
        freqTable = new float[256];
        gainTable = new float[256];
        qTable = new float[256];

        for (int i = 0; i < 256; i++) {
            // Create freqTable (1 to 38500)
            if (i < 256 / 8 * 3) {
                freqTable[i] = i + 1;
            } else if (i < 256 / 8 * 5) {
                freqTable[i] = (i - 256 / 8 * 3) * 10 + 100;
            } else if (i < 256 / 8 * 7) {
                freqTable[i] = (i - 256 / 8 * 5) * 100 + 800;
            } else {
                freqTable[i] = (i - 256 / 8 * 7) * 1000 + 7500;
            }

            // Create a gainTable (-20 to +19.84375)
            if (i < 128) {
                gainTable[i] = (float) (-20.0 / 128.0 * (128 - i));
            } else {
                gainTable[i] = (float) (20.0 / 128.0 * (i - 128));
            }

            // Creating a QTable (0.1 to 20.0)
            if (i < 256 / 8 * 3) {
                // 0-95 : 0.01041667 ～ 1.0
                qTable[i] = (float) (1.0 / (256 / 8 * 3) * (i + 1));
            } else if (i < 256 / 8 * 6) {
                // 96-191 : 1.104167 ～ 11.0

                qTable[i] = (float) (10.0 / (256 / 8 * 3) * (i + 1 - 256 / 8 * 3) + 1.0);
            } else {
                // 192-255 : 11.15625 ～ 21.0
                qTable[i] = (float) (10.0 / (256 / 8 * 2) * (i + 1 - 256 / 8 * 6) + 11.0);
            }
        }
    }
}
