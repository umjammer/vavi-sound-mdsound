package mdsound.instrument;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.CtrQsound;


public class CtrQSoundInst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 4000000;
    public static final int MAX_CHIPS = 0x02;

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

    @Override
    public void init() {
        mask[0] = 0;
        mask[1] = 0;
    }

    @Override
    public void reset(int chipId) {
        CtrQsound chip = chips[chipId];
        chip.reset();
        // need to wait until the chips is ready before we start writing to it ...
        // we do this by time travel.
        waitBusy(chipId);

        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        CtrQsound chip = chips[chipId];
        return chip.start2(clock);
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        CtrQsound chip = chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int read(int chipId, int adr) {
        CtrQsound chip = chips[chipId];
        return chip.read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        CtrQsound chip = chips[chipId];
        chip.write2(adr, data);
        return 0;
    }

    public void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        writePcm(chipId, romSize, dataStart, dataLength, romData, 0);
    }

    private void setMuteMask(int chipId, int muteMask) {
        CtrQsound chip = chips[chipId];
        if (chip == null) return;
        chip.setMuteMask(muteMask);
    }

    private void writeData(int chipId, byte address, int data) {
        CtrQsound chip = chips[chipId];
        chip.writeData(address, data);
    }

    private void waitBusy(int chipId) {
        CtrQsound chip = chips[chipId];
        chip.waitBusy();
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

    public synchronized void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        CtrQsound chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }
}
