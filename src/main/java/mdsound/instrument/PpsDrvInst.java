package mdsound.instrument;

import java.util.function.BiConsumer;

import mdsound.Instrument;
import mdsound.chips.PPS;


public class PpsDrvInst extends Instrument.BaseInstrument {

    private PPS[] chips = new PPS[] {new PPS(), new PPS()};

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

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, clock, 0);
    }

    /** @param option BiConsumer&lt;Integer, Integer&gt; */
    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        PPS chip = chips[chipId];
        return chip.start(clock, (option != null && option.length > 0) ? (BiConsumer<Integer, Integer>) option[0] : null);
    }

    @Override
    public void stop(int chipId) {
        PPS chip = chips[chipId];
        chip.stop();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        PPS chip = chips[chipId];
        return chip.write(port, adr, data);
    }

    // 音量設定
    private void setVolume(int chipId, int vol) {
        PPS chip = chips[chipId];
        chip.setVolume(vol);
    }

    private void play(int chipId, int al, int bh, int bl) {
        PPS chip = chips[chipId];
        chip.play(al, bh, bl);
    }

    private void stop_(int chipId) {
        PPS chip = chips[chipId];
        chip.stop();
    }

    private boolean setParam(int chipId, int paramno, int data) {
        PPS chip = chips[chipId];
        return chip.setParam(paramno, data);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        PPS chip = chips[chipId];
        chip.update(outputs, samples);
    }

    public int load(int chipId, byte[] pcmData) {
        PPS chip = chips[chipId];
        return chip.load(pcmData);
    }
}

