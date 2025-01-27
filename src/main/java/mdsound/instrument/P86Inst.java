package mdsound.instrument;

import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.P86;


public class P86Inst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

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

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    @Override
    public synchronized void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        chip.loadPcm(0, 0, 0, buf);
    }
}
