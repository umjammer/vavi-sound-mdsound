package mdsound.instrument;

import java.util.function.BiConsumer;

import mdsound.Instrument;
import mdsound.chips.PPS;


public class PpsDrvInst extends Instrument.BaseInstrument {

    private final PPS[] chips = {new PPS(), new PPS()};

    @Override
    public String getName() {
        return "PpsDrvInst";
    }

    @Override
    public String getShortName() {
        return "PpsDrvInst";
    }

    @Override
    public void reset(int chipId) {
        PPS chip = chips[chipId];
        chip.reset();
    }

    /** @param option BiConsumer&lt;Integer, Integer&gt; */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        PPS chip = chips[chipId];
        return chip.start(samplingRate, (option != null && option.length > 0) ? (BiConsumer<Integer, Integer>) option[0] : null);
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        PPS chip = chips[chipId];
        return chip.write(port, adr, data);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        PPS chip = chips[chipId];
        chip.update(outputs, samples);
    }

    @Override
    public void stop(int chipId) {
        PPS chip = chips[chipId];
        chip.stop();
    }

    /** Sets volume. */
    public void setVolume(int chipId, int vol) {
        PPS chip = chips[chipId];
        chip.setVolume(vol);
    }

    public void play(int chipId, int al, int bh, int bl) {
        PPS chip = chips[chipId];
        chip.play(al, bh, bl);
    }

    public boolean setParam(int chipId, int paramno, int data) {
        PPS chip = chips[chipId];
        return chip.setParam(paramno, data);
    }

    // ----

    public synchronized void writePcm(int chipId, byte[] pcmData) {
        PPS chip = chips[chipId];
        chip.load(pcmData);
    }
}

