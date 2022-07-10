package mdsound.chips;


/**
 * SinWaveGen.
 */
public class SinWaveGen {
    public boolean render = true;

    public double clock = 44100.0;
    private double tone = 440.0;
    private double delta = 0;

    public void update(int[][] outputs, int samples) {
        if (!render) return;

        for (int i = 0; i < samples; i++) {
            double d = (Math.sin(delta * Math.PI / 180.0) * 4000.0);
            int n = (int) d;
            delta += 360.0f * tone / clock;

            outputs[0][i] = n;
            outputs[1][i] = n;
        }
    }

    public int write(int data) {
        render = (data != 0);
        return 0;
    }
}
