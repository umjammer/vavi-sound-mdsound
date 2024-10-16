package mdsound.instrument;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.CtrQsound;


public class CtrQSoundInst extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "QSound_ctr";
    }

    @Override
    public String getShortName() {
        return "QSNDc";
    }

    @Override
    public void reset(int chipId) {
        device_reset_qsound(chipId);

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int clock) {
        return device_start_qsound(chipId, 4000000);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        return device_start_qsound(chipId, clockValue);
    }

    @Override
    public void stop(int chipId) {
        device_stop_qsound(chipId);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        qsound_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        qsound_w(chipId, adr, data);
        return 0;
    }

    private int device_start_qsound(int chipId, int clock) {
        CtrQsound chip = QSoundData[chipId];
        return chip.start2(clock);
    }

    private void device_stop_qsound(int chipId) {
        device_stop_qsound_ctr(chipId);
    }

    private void device_reset_qsound(int chipId) {
        device_reset_qsound_ctr(chipId);
        // need to wait until the chips is ready before we start writing to it ...
        // we do this by time travel.
        qsoundc_wait_busy(chipId);
    }

    public void qsound_w(int chipId, int offset, int data) {
        CtrQsound chip = QSoundData[chipId];
        chip.write2(offset, data);
    }

    private void qsound_update(int chipId, int[][] outputs, int samples) {
        qsoundc_update(chipId, outputs, samples);
    }

    public void qsound_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        qsoundc_write_rom(chipId, romSize, dataStart, dataLength, romData, 0);
    }

    public void qsound_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        qsoundc_write_rom(chipId, romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void qsound_set_mute_mask(int chipId, int muteMask) {
        qsoundc_set_mute_mask(chipId, muteMask);
    }

    private static final int MAX_CHIPS = 0x02;
    CtrQsound[] QSoundData = new CtrQsound[MAX_CHIPS];

    private int device_start_qsound_ctr(int chipId, int clock) {
        QSoundData[chipId] = new CtrQsound();
        CtrQsound chip = QSoundData[chipId];
        return chip.start(clock);
    }

    private void device_stop_qsound_ctr(int chipId) {
        CtrQsound chip = QSoundData[chipId];
    }

    private void device_reset_qsound_ctr(int chipId) {
        CtrQsound chip = QSoundData[chipId];
        chip.reset();
    }

    private byte qsoundc_r(int chipId, int offset) {
        CtrQsound chip = QSoundData[chipId];
        return chip.read(offset);
    }

    private void qsoundc_w(int chipId, int offset, byte data) {
        CtrQsound chip = QSoundData[chipId];
        chip.write(offset, data);
    }

    private void qsoundc_write_data(int chipId, byte address, int data) {
        CtrQsound chip = QSoundData[chipId];
        chip.writeData(address, data);
    }

    private void qsoundc_update(int chipId, int[][] outputs, int samples) {
        CtrQsound chip = QSoundData[chipId];
        chip.update(outputs, samples);
    }

    private void qsoundc_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        CtrQsound chip = QSoundData[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    private void qsoundc_set_mute_mask(int chipId, int muteMask) {
        CtrQsound chip = QSoundData[chipId];
        if (chip == null) return;
        chip.setMuteMask(muteMask);
    }

    private void qsoundc_wait_busy(int chipId) {
        CtrQsound chip = QSoundData[chipId];
        chip.waitBusy();
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }
}
