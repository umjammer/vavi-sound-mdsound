package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.QSound;


public class QSoundInst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = QSound.CLOCK;
    public static final int MAX_CHIPS = 0x02;

    private final QSound[] chips = {new QSound(), new QSound()};

    private final int[] mask = {0, 0};

    public QSoundInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public void init() {
        mask[0] = 0;
        mask[1] = 0;
    }

    @Override
    public String getName() {
        return "QSound";
    }

    @Override
    public String getShortName() {
        return "QSND";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;
        return chips[chipId].start(clock);
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

    public void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        writePcm(chipId, romSize, dataStart, dataLength, romData, 0);
    }

    private void setMuteMask(int chipId, int muteMask) {
        chips[chipId].setMuteMask(muteMask);
    }

    //----

    public synchronized void setMask(int chipId, int ch) {
        ch = (1 << ch);
        mask[chipId] |= ch;
        setMuteMask(chipId, mask[chipId]);
    }

    public synchronized void resetMask(int chipId, int ch) {
        ch = (1 << ch);
        mask[chipId] &= ~(int) ch;
        setMuteMask(chipId, mask[chipId]);
    }

    public synchronized void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        chips[chipId].writeRom(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "Q-Sound");
            case "FAMILY" -> result.put(getName(), "Capcom custom");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
