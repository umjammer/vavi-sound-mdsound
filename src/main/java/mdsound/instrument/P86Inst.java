package mdsound.instrument;

import mdsound.Instrument;
import mdsound.chips.P86;


public class P86Inst extends Instrument.BaseInstrument {

    private final P86 chip = new P86();

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
        chip.init();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chip.start(samplingRate);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        return chip.write(port, adr, data);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chip.update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        // none
    }

    // ----

    public synchronized void writePcm(int chipId, int address, int data, byte[] pcmData) {
        chip.loadPcm(0, address, data, pcmData);
    }
}
