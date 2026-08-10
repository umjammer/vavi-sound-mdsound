/*
 * NSFPlay/NFSPlug project by Brezza.
 *
 * https://web.archive.org/web/20160301201825/http://www.pokipoki.org/dsa/
 */

package mdsound.np;

import mdsound.np.Device.Renderable;


//
// Excerpt from filter.h
//
public class Filter {

    private Renderable target;
    protected int type;
    private final int[] _out = new int[2];
    private double a;
    private double rate;
    private double r;
    private double C;
    private final boolean disable;
    private final int getaBits;

    public Filter() {
        this.getaBits = 20;
        this.target = null;
        this.rate = 48000; // DEFAULT_RATE;
        this.r = 4700;
        this.C = 10.0E-9;
        this.disable = false;
        this._out[0] =this. _out[1] = 0;
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

    public void setParam(double r, int c) { // c = 0-400, 0=off, 400=max
        // C = 1.0E-10 * c;
        this.r = r;

        this.C = Math.pow((double) (c) / 400.0, 2.0) * 1.0E-10 * 400.0;
        // curved to try to provide useful range of settings
        // LPF = 112 ~ my NES

        updateFactor();
    }

    public void setClock(double clock) {
        reset();
    }

    public void setRate(double r) {
        this.rate = r;
        updateFactor();
    }

    private void updateFactor() {
        if (r != 0.0 && C != 0.0 && rate != 0.0)
            this.a = (1.0 / rate) / ((r * C) + (1.0 / rate));
        else
            this.a = 2.0; // disabled
    }

    public double getFactor() {
        return a;
    }

    public void reset() {
        updateFactor();
        _out[0] = _out[1] = 0;
    }
}
