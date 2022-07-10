
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
    public void reset(byte chipId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int start(byte chipId, int clock) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop(byte chipId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        throw new UnsupportedOperationException();
    }

}
