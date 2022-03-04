
package mdsound.np;

import mdsound.np.IDevice.IRenderable;

//
// filter.h から抜粋
//
//
public class Filter {
    protected IRenderable target;

    protected int type;

    protected int[] _out = new int[2];

    protected double a;

    protected double rate, R, C;

    protected boolean disable;

    protected int GETA_BITS;

    public Filter() {
        GETA_BITS = (20);
        target = null;
        rate = 48000;// DEFAULT_RATE;
        R = 4700;
        C = 10.0E-9;
        disable = false;
        _out[0] = _out[1] = 0;
    }

    protected void finalinze() {
    }

    public void Attach(IRenderable t) {
        target = t;
    }

    public int FastRender(int[] b)// [2])
    {
        if (target != null)
            target.Render(b);
        if (a < 1.0) {
            _out[0] += (int) (a * (b[0] - _out[0]));
            _out[1] += (int) (a * (b[1] - _out[1]));
            b[0] = _out[0];
            b[1] = _out[1];
        }
        return 2;
    }

    public void Tick(int clocks) {
        if (target != null)
            target.Tick(clocks);
    }

    public int Render(int[] b)// [2])
    {
        return FastRender(b);
    }

    public void SetParam(double r, int c) // c = 0-400, 0=off, 400=max
    {
        // C = 1.0E-10 * c;
        R = r;

        C = Math.pow((double) (c) / 400.0, 2.0) * 1.0E-10 * 400.0;
        // curved to try to provide useful range of settings
        // LPF = 112 ~ my NES

        UpdateFactor();
    }

    public void SetClock(double clock) {
        Reset();
    }

    public void SetRate(double r) {
        rate = r;
        UpdateFactor();
    }

    public void UpdateFactor() {
        if (R != 0.0 && C != 0.0 && rate != 0.0)
            a = (1.0 / rate) / ((R * C) + (1.0 / rate));
        else
            a = 2.0; // disabled
    }

    public double GetFactor() {
        return a;
    }

    public void Reset() {
        UpdateFactor();
        _out[0] = _out[1] = 0;
    }
}
