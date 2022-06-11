
package mdsound.np;

import mdsound.np.Device.Renderable;

//
// filter.h から抜粋
//
//
public class Filter {

    protected Renderable target;
    protected int type;
    protected int[] _out = new int[2];
    protected double a;
    protected double rate, r, C;
    protected boolean disable;
    protected int getaBits;

    public Filter() {
        getaBits = 20;
        target = null;
        rate = 48000;// DEFAULT_RATE;
        r = 4700;
        C = 10.0E-9;
        disable = false;
        _out[0] = _out[1] = 0;
    }

    protected void finalinze() {
    }

    public void attach(Renderable t) {
        target = t;
    }

    public int fastRender(int[] b) {
        if (target != null)
            target.render(b);
        if (a < 1.0) {
            _out[0] += (int) (a * (b[0] - _out[0]));
            _out[1] += (int) (a * (b[1] - _out[1]));
            b[0] = _out[0];
            b[1] = _out[1];
        }
        return 2;
    }

    public void tick(int clocks) {
        if (target != null)
            target.tick(clocks);
    }

    public int render(int[] b) {
        return fastRender(b);
    }

    public void SetParam(double r, int c) { // c = 0-400, 0=off, 400=max
        // C = 1.0E-10 * c;
        this.r = r;

        C = Math.pow((double) (c) / 400.0, 2.0) * 1.0E-10 * 400.0;
        // curved to try to provide useful range of settings
        // LPF = 112 ~ my NES

        updateFactor();
    }

    public void setClock(double clock) {
        reset();
    }

    public void setRate(double r) {
        rate = r;
        updateFactor();
    }

    public void updateFactor() {
        if (r != 0.0 && C != 0.0 && rate != 0.0)
            a = (1.0 / rate) / ((r * C) + (1.0 / rate));
        else
            a = 2.0; // disabled
    }

    public double getFactor() {
        return a;
    }

    public void reset() {
        updateFactor();
        _out[0] = _out[1] = 0;
    }
}
