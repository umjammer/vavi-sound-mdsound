package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.DosboxYm3812;


public class Ym3812Inst extends Instrument.BaseInstrument {

    /** the carrier operator's offset from 0x40, per channel */
    private static final int[] CARRIER = {3, 4, 5, 11, 12, 13, 19, 20, 21};


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
    public Map<String, Object> getView(int chipId, String key, Object... args) {
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
            case "info" -> {
                // this core keeps the register file, so the channel state is decoded from it
                byte[] regs = chips[chipId].getRegisters();
                for (int ch = 0; ch < DosboxYm3812.CHANNELS; ch++) {
                    int regB = regs[0xb0 + ch] & 0xff;
                    result.put("channels." + ch + ".keyOn", (regB & 0x20) != 0);
                    result.put("channels." + ch + ".fnum", ((regB & 0x03) << 8) | (regs[0xa0 + ch] & 0xff));
                    result.put("channels." + ch + ".block", (regB >> 2) & 0x07);
                    result.put("channels." + ch + ".totalLevel", regs[0x40 + CARRIER[ch]] & 0x3f);
                    result.put("channels." + ch + ".mute", false);
                }
            }
            case "register" -> {
                byte[] raw = chips[chipId].getRegisters();
                int[] copy = new int[raw.length];
                for (int i = 0; i < raw.length; i++) copy[i] = raw[i] & 0xff;
                result.put("register", copy);
            }
        }
        return result;
    }

    /** whether the operator is a carrier, from the connection bit the chip holds */
    public boolean isCarrier(int chipId, int ch, int slot) {
        return slot == 1 || (chips[chipId].getRegisters()[0xc0 + ch] & 1) != 0;
    }

    /** whether the rhythm section is on - register {@code 0xbd} bit 5 */
    public boolean isRhythm(int chipId) {
        return (chips[chipId].getRegisters()[0xbd] & 0x20) != 0;
    }
}
