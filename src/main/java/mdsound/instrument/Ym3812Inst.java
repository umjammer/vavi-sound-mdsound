package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.DosboxYm3812;


public class Ym3812Inst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;
    public static final int DefaultClockValue = 3579545;

    private final DosboxYm3812[] chips = {new DosboxYm3812(), new DosboxYm3812()};

    private int emuCore;

    public Ym3812Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public String getName() {
        return "YM3812";
    }

    @Override
    public String getShortName() {
        return "OPL2";
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        int rate = (clock & 0x7fff_ffff) / 72;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) || CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        chips[chipId].start(emuCore, clock, rate, this::updateStream);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < MAX_CHIPS;
        chips[chipId].write(0, adr);
        chips[chipId].write(1, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].updateStream(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    @Override
    public void setMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(ch); // TODO
    }

    @Override
    public void resetMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(~ch); // TODO
    }

    private final int[][] dummyBuf = {null, null};

    private void updateStream(/*, int interval*/) {
        chips[0].updateStream(dummyBuf, 0); // TODO
    }

    public void setEmuCore(int Emulator) {
        emuCore = (Emulator < 0x02) ? Emulator : 0x00;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "YM3812");
            case "FAMILY" -> result.put(getName(), "Yamaha FM");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
            case "statusPort" -> result.put(getName(), read(chipId, 0));
            case "port" -> result.put(getName(), read(chipId, 1));
        }
        return result;
    }
}
