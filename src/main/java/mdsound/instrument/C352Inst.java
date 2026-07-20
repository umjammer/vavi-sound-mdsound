
package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.C352;


public class C352Inst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final C352[] chips = {new C352(), new C352()};

    /** the buffer the "register" view is read back into, one per chip */
    private final int[][] registers = {new int[0x203], new int[0x203]};

    public C352Inst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "C352";
    }

    @Override
    public String getShortName() {
        return "C352";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... Option) {
        assert chipId < MAX_CHIPS;

        int clockDivider;
        if (Option == null || Option.length < 1) clockDivider = 0;
        else clockDivider = (int) Option[0];

        return chips[chipId].start(clock, clockDivider * 4);
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    @Override
    public void setMask(int chipId, int ch) {
        int muteMask = chips[chipId].getMuteMask() | ch;
        chips[chipId].setMuteMask(muteMask);
    }

    @Override
    public void resetMask(int chipId, int ch) {
        int muteMask = chips[chipId].getMuteMask() & ~ch;
        chips[chipId].setMuteMask(muteMask);
    }

    /** @param extras 0: srcOffset, 1: romSize */
    @Override
    public synchronized void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writeRom(romSize, offset, length, buf, srcOffset);
    }

    //----

    /**
     * used for volume also
     * @see mdsound.MDSound.Chip.SetVolume
     */
    public void setRearMute(String tag, int vol, double ignored) {
        C352.setOptions(vol & 0xff); // TODO ugly
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x40, 8d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "flags" -> result.put("flags", chips[chipId].getFlags());
            case "register" -> result.put("register", readRegisters(chipId));
        }
        return result;
    }

    /**
     * The channel registers as the chip holds them now, eight per channel, plus the control
     * register at {@code 0x200}. The buffer is reused, so a caller that wants to keep the values
     * has to copy them.
     */
    private int[] readRegisters(int chipId) {
        int[] buf = registers[chipId];
        for (int adr = 0; adr < 0x100; adr++) {
            buf[adr] = chips[chipId].read(adr);
        }
        buf[0x200] = chips[chipId].read(0x200);
        return buf;
    }
}
