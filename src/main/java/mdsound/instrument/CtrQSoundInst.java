package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.CtrQsound;


public class CtrQSoundInst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    public static final int DefaultClockValue = 4000000;
    private static final int MAX_CHIPS = 0x02;

    private final CtrQsound[] chips = {new CtrQsound(), new CtrQsound()};

    private final int[] mask = {0, 0};

    @Override
    public String getName() {
        return "QSound_ctr";
    }

    @Override
    public String getShortName() {
        return "QSNDc";
    }

    public CtrQSoundInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public void init() {
        mask[0] = 0;
        mask[1] = 0;
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
        // need to wait until the chips is ready before we start writing to it ...
        // we do this by time travel.
        chips[chipId].waitBusy();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        return chips[chipId].start2(clock);
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write2(adr, data);
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
    }

    @Override
    public synchronized void setMask(int chipId, int ch) {
        assert chipId < MAX_CHIPS;
        mask[chipId] |= (1 << ch);
        chips[chipId].setMuteMask(mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        assert chipId < MAX_CHIPS;
        mask[chipId] &= ~(1 << ch);
        chips[chipId].setMuteMask(mask[chipId]);
    }

    /** @param extras 0: srcOffset, 1: romSize */
    @Override
    public synchronized void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writeRom(romSize, offset, length, buf, srcOffset);
    }

    public void writeData(int chipId, byte address, int data) {
        chips[chipId].writeData(address, data);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "NAME" -> result.put(getName(), "Q-Sound");
            case "info" -> {
                CtrQsound chip = chips[chipId];
                int[] regs = chip.getRegisterMap();
                for (int v = 0; v < CtrQsound.VOICES; v++) {
                    result.put("channels." + v + ".rate", regs[(v << 3) + 2]);
                    result.put("channels." + v + ".volume", regs[(v << 3) + 6]);
                    result.put("channels." + v + ".bank", regs[(((v - 1 + 16) % 16) << 3) + 0]);
                    result.put("channels." + v + ".pan", regs[v + 0x80]);
                    result.put("channels." + v + ".mute", chip.isMuted(v));
                }
            }
        }
        return result;
    }
}
