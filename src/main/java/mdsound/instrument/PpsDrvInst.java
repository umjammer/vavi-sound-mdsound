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
    public void reset(byte chipId) {
        PPS chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, clock, 0);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        PPS chip = chips[chipId];
        return chip.start(clock, (option != null && option.length > 0) ? (BiConsumer<Integer, Integer>) option[0] : null);
    }

    @Override
    public void stop(byte chipId) {
        PPS chip = chips[chipId];
        chip.stop();
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        PPS chip = chips[chipId];
        return chip.write(port, adr, data);
    }

    // 音量設定
    private void setVolume(byte chipId, int vol) {
        PPS chip = chips[chipId];
        chip.setVolume(vol);
    }

    private void play(byte chipId, byte al, byte bh, byte bl) {
        PPS chip = chips[chipId];
        chip.play(al, bh, bl);
    }

    private void stop_(byte chipId) {
        PPS chip = chips[chipId];
        chip.stop();
    }

    private boolean setParam(byte chipId, byte paramno, byte data) {
        PPS chip = chips[chipId];
        return chip.setParam(paramno, data);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        PPS chip = chips[chipId];
        chip.update(outputs, samples);
    }

    public int load(byte chipId, byte[] pcmData) {
        PPS chip = chips[chipId];
        return chip.load(pcmData);
    }
}

