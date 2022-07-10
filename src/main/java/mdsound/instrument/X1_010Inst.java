package mdsound.instrument;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.X1_010;


public class X1_010Inst extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "X1-010";
    }

    @Override
    public String getShortName() {
        return "X1-010";
    }

    public X1_010Inst() {
    }

    @Override
    public void reset(byte chipId) {
        device_reset_x1_010(chipId);
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_x1_010(chipId, 16000000);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_x1_010(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_x1_010(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        seta_update(chipId, outputs, samples);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        seta_sound_w(chipId, (port << 8) | adr, (byte) data);
        return 0;
    }

    private static final int MAX_CHIPS = 0x02;
    private X1_010[] x1010Data = new X1_010[] {new X1_010(), new X1_010()};

    private void seta_update(byte chipId, int[][] outputs, int samples) {
        X1_010 info = x1010Data[chipId];
        info.update(outputs, samples);
    }

    private int device_start_x1_010(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        X1_010 info = x1010Data[chipId];

        int rate = clock / 512;
        if (((Instrument.BaseInstrument.CHIP_SAMPLING_MODE & 0x01) != 0 && rate < Instrument.BaseInstrument.CHIP_SAMPLE_RATE) ||
                Instrument.BaseInstrument.CHIP_SAMPLING_MODE == 0x02)
            rate = Instrument.BaseInstrument.CHIP_SAMPLE_RATE;
        info.start(clock, rate);
        return rate;
    }

    private void device_stop_x1_010(byte chipId) {
        X1_010 info = x1010Data[chipId];
        info.stop();
    }

    private void device_reset_x1_010(byte chipId) {
        X1_010 info = x1010Data[chipId];
        info.reset();
    }

    private byte seta_sound_r(byte chipId, int offset) {
        X1_010 info = x1010Data[chipId];
        return info.read(offset);
    }

    private void seta_sound_w(byte chipId, int offset, byte data) {
        X1_010 info = x1010Data[chipId];
        info.write(offset, data);
    }

    public void x1_010_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int romDataStartAddress/* = 0*/) {
        X1_010 info = x1010Data[chipId];
        info.writeRom(romSize, dataStart, dataLength, romData, romDataStartAddress);
    }

    public void x1_010_set_mute_mask(byte chipId, int muteMask) {
        X1_010 info = x1010Data[chipId];
        info.setMuteMask(muteMask);
    }

    /**
     * Generic get_info
     */
  /*DEVICE_GET_INFO( X1_010Inst ) {
    case DEVINFO_STR_NAME:       strcpy(info.s, "X1-010");      break;
    case DEVINFO_STR_FAMILY:     strcpy(info.s, "Seta custom");     break;
    case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
    case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
  */

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }
}
