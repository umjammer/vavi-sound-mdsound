package mdsound.instrument;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.PokeyState;


public class PokeyInst extends Instrument.BaseInstrument {
    @Override
    public String getName() {
        return "POKEY";
    }

    @Override
    public String getShortName() {
        return "POKEY";
    }

    @Override
    public void reset(byte chipId) {
        device_reset_pokey(chipId);
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_pokey(chipId, 1789772);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        return device_start_pokey(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_pokey(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        pokey_update(chipId, outputs, samples);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        pokey_w(chipId, adr, (byte) data);
        return 0;
    }

    private static final int MAX_CHIPS = 0x02;
    private PokeyState[] chips = new PokeyState[MAX_CHIPS];

    private void pokey_update(byte chipId, int[][] outputs, int samples) {
        PokeyState chip = chips[chipId];
        chip.update(outputs, samples);
    }

    private int device_start_pokey(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        if (chips[chipId] == null) {
            chips[chipId] = new PokeyState();
        }

        PokeyState chip = chips[chipId];
        return chip.start(clock);
    }

    private void device_stop_pokey(byte chipId) {
        PokeyState chip = chips[chipId];
    }

    private void device_reset_pokey(byte chipId) {
        PokeyState chip = chips[chipId];
        chip.reset();
    }

    private byte pokey_r(byte chipId, int offset) {
        PokeyState chip = chips[chipId];
        return chip.read(offset);
    }

    private void pokey_w(byte chipId, int offset, byte data) {
        PokeyState chip = chips[chipId];
        chip.write(offset, data);
    }

    private void pokey_set_mute_mask(byte chipId, int muteMask) {
        PokeyState chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    /**
     * Generic get_info
     */
    /*DEVICE_GET_INFO( PokeyInst ) {
      case DEVINFO_STR_NAME:       strcpy(info.s, "POKEY");      break;
      case DEVINFO_STR_FAMILY:     strcpy(info.s, "Atari custom");    break;
      case DEVINFO_STR_VERSION:     strcpy(info.s, "4.51");      break;
      case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
    */

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }
}
