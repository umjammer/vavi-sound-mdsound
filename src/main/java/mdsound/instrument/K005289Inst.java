
package mdsound.instrument;

import mdsound.Instrument;


public class K005289Inst extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "K005289Inst";
    }

    @Override
    public String getShortName() {
        return "K005";
    }

    @Override
    public void reset(int chipId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int start(int chipId, int clock) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop(int chipId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        throw new UnsupportedOperationException();
    }
}
