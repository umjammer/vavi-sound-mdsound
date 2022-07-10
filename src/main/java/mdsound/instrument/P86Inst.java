package mdsound.instrument;

import mdsound.Instrument;
import mdsound.chips.P86;


public class P86Inst extends Instrument.BaseInstrument {

    P86 info = new P86();

    @Override
    public String getName() {
        return "PC-9801-86";
    }

    @Override
    public String getShortName() {
        return "P86Inst";
    }

    @Override
    public void reset(byte chipId) {
        info.init();
    }

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, clock, 0);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        info.samplingRate = clock;
        reset(chipId);
        return clock;
    }

    @Override
    public void stop(byte chipId) {
        // none
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        info.update(outputs, samples);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        return info.write(port, adr, data);
    }

    public int loadPcm(byte chipId, byte address, byte data, byte[] pcmData) {
        return info.loadPcm(0, address, data, pcmData);
    }
}
