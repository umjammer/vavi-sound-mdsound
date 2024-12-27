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
    public void reset(int chipId) {
        info.init();
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, clock, 0);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        info.samplingRate = samplingRate;
        reset(chipId);
        return samplingRate;
    }

    @Override
    public void stop(int chipId) {
        // none
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        info.update(outputs, samples);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        return info.write(port, adr, data);
    }

    public int loadPcm(int chipId, int address, int data, byte[] pcmData) {
        return info.loadPcm(0, address, data, pcmData);
    }
}
