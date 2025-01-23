package mdsound.instrument;

import java.util.function.BiConsumer;

import mdsound.Instrument;
import mdsound.chips.PPS;


public class PpsInst extends Instrument.BaseInstrument {

    private final PPS[] chips = {new PPS(), new PPS()};

    @Override
    public String getName() {
        return "PPS";
    }

    @Override
    public String getShortName() {
        return "PPS";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    /** @param option BiConsumer&lt;Integer, Integer&gt; */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        return chips[chipId].start(samplingRate, (option != null && option.length > 0) ? (BiConsumer<Integer, Integer>) option[0] : null);
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        return chips[chipId].write(port, adr, data);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    /** Sets volume. */
    public void setVolume(int chipId, int vol) {
        chips[chipId].setVolume(vol);
    }

    public void play(int chipId, int al, int bh, int bl) {
        chips[chipId].play(al, bh, bl);
    }

    public boolean setParam(int chipId, int paramno, int data) {
        return chips[chipId].setParam(paramno, data);
    }

    // ----

    public synchronized void writePcm(int chipId, byte[] pcmData) {
        chips[chipId].load(pcmData);
    }
}

