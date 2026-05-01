package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.ScdPcm;


/**
 * RF5C164
 */
public class ScdPcmInst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final ScdPcm[] chips = {new ScdPcm(), new ScdPcm()};

    private final int[][][] volumes = {
            {{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}},
            {{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}}
    };

    public ScdPcmInst() {
        // 0..Main
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "RF5C164";
    }

    @Override
    public String getShortName() {
        return "RF5C";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    // samplingRate unused
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        int rate = (clock & 0x7fff_ffff) / 384;
        if (((CHIP_SAMPLING_MODE & 0x01) != 0 && rate < CHIP_SAMPLE_RATE) || CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        chips[chipId].init(rate);
        chips[chipId].start(clock);
        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].writeReg(adr, data);
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
        chips[chipId].setMuteMask(1);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        chips[chipId].setMuteMask(0);
    }

    /** @param extras 0: srcOffset */
    @Override
    public synchronized void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        chips[chipId].writeRam2(offset, length, buf, srcOffset);
    }

    public void setRate(int chipId, int rate) {
        chips[chipId].setRate(rate);
    }

    // ----

    public synchronized void writeMemory(int chipId, int adr, int data) {
        chips[chipId].writeMem(adr, data);
    }

    public synchronized int[][] readVolumes(int chipId) {
        return volumes[chipId];
    }

    public synchronized Map<String, Object> getInfo(int chipId) {
        ScdPcm rf5c164Register = chips[chipId];

        Map<String, Object> newParam = new HashMap<>();
        for (int ch = 0; ch < 8; ch++) {
            newParam.put("channels." + ch + ".enable", rf5c164Register.getChannel(ch).enable != 0);
            newParam.put("channels." + ch + ".stepB", rf5c164Register.getChannel(ch).stepB);
            newParam.put("channels." + ch + ".mulL", rf5c164Register.getChannel(ch).mulL);
            newParam.put("channels." + ch + ".mulR", rf5c164Register.getChannel(ch).mulR);
            newParam.put("channels." + ch + ".pan",  rf5c164Register.getChannel(ch).pan);
        }
        return newParam;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x80, 2d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        }
        return result;
    }
}
