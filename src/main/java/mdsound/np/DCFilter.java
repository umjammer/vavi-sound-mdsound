package mdsound.np;

//
// filter.h から抜粋
//
//


//  dcf.SetParam(270,(*config)["HPF"]); //DCFilter
//  lpf.SetParam(4700.0,(*config)["LPF"]); //Filter


public class DCFilter {

    private double r, c;
    private double a;
    private double[] in = new double[2], out = new double[2];
    private double rate;

    public DCFilter() {
        r = c = 0.0;
        reset();
    }

    protected void finalinze() {
    }

    public void updateFactor() {
        if (c == 0.0 || r == 0.0) {
            a = 2.0; // disable
        } else {
            a = (r * c) / ((r * c) + (1.0 / rate));
        }
    }

    public double getFactor() {
        return a;
    }

    public void setRate(double r) {
        // カットオフ周波数 : 2pi*R*C
        rate = r;
        updateFactor();
    }

    // c = 0-256, 256 = off, 0 = max
    public void setParam(double r, int c) {
        this.r = r;
        //C = c;

        if (c > 255)
            this.c = 0.0; // disable
        else
            this.c = 2.0e-4 * (1.0 - Math.pow(1.0 - ((double) (c + 1) / 256.0), 0.05));

        // the goal of this curve is to have a wide range of practical use,
        // though it may look a little complicated. points of interest:
        //   HPF = 163 ~ my NES
        //   HPF = 228 ~ my Famicom
        //   low values vary widely and have an audible effect
        //   high values vary slowly and have a visible effect on DC offset

        updateFactor();
    }

    // 非virtualなRender
    public int fastRender(int[] b) {
        if (a < 1.0) {
            out[0] = a * (out[0] + b[0] - in[0]);
            in[0] = b[0];
            b[0] = (int) out[0];

            out[1] = a * (out[1] + b[1] - in[1]);
            in[1] = b[1];
            b[1] = (int) out[1];
        }
        return 2;
    }

    public int render(int[] b) {
        return fastRender(b);
    }

    public void tick(int clocks) {
    }

    public void reset() {
        in[0] = in[1] = 0;
        out[0] = out[1] = 0;
    }

    public void setLevel(int[] b) {
        in[0] = b[0];
        in[1] = b[1];
    }
}
