package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.MDSound;
import mdsound.chips.Nes;
import mdsound.np.NpNesFds;


public class IntFNesInst extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipId) {
        device_reset_nes(chipId);
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_nes(chipId, clock);
    }

    @Override
    public int start(byte chipId, int samplingRate, int clockValue, Object... Option) {
        return device_start_nes(chipId, clockValue);
    }

    @Override
    public void stop(byte chipId) {
        device_stop_nes(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        nes_stream_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    public IntFNesInst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    private static final byte MAX_CHIPS = 0x02;
    private Nes[] chips = new Nes[] {new Nes(), new Nes()};

    @Override
    public String getName() {
        return "NES";
    }

    @Override
    public String getShortName() {
        return "NES";
    }

    public void nes_stream_update(byte chipId, int[][] outputs, int samples) {
        Nes info = chips[chipId];
        info.update(outputs, samples);
    }

    private int device_start_nes(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;
        Nes info = chips[chipId];

        int rate = clock / 4;
        if ((Instrument.BaseInstrument.CHIP_SAMPLING_MODE == 0x01 && rate < Instrument.BaseInstrument.CHIP_SAMPLE_RATE) ||
                Instrument.BaseInstrument.CHIP_SAMPLING_MODE == 0x02)
            rate = Instrument.BaseInstrument.CHIP_SAMPLE_RATE;
        info.start(clock, rate);
        return rate;
    }

    private void device_stop_nes(byte chipId) {
        Nes info = chips[chipId];
        info.stop();
    }

    private void device_reset_nes(byte chipId) {
        Nes chip = chips[chipId];
        chip.reset();
    }

    private void nes_w(byte chipId, int offset, byte data) {
        Nes chip = chips[chipId];
        chip.write(offset, data);
    }

    public void nes_write_ram(byte chipId, int dataStart, int dataLength, byte[] ramData) {
        nes_write_ram(chipId, dataStart, dataLength, ramData, 0);
    }

    public void nes_write_ram(byte chipId, int dataStart, int dataLength, byte[] ramData, int ramdataStartAdr) {
        Nes chip = chips[chipId];
        chip.writeRam(dataStart, dataLength, ramData, ramdataStartAdr);
    }

    public byte[] nes_r_apu(byte chipId) {
        Nes chip = chips[chipId];
        return chip.readApu();
    }

    public byte[] nes_r_dmc(byte chipId) {
        Nes chip = chips[chipId];
        return chip.readDmc();
    }

    public NpNesFds nes_r_fds(byte chipId) {
        Nes chip = chips[chipId];
        return chip.readDds();
    }

    private void nes_set_emu_core(byte emulator) {
    }

    private void nes_set_chip_option(byte chipId) {
        Nes chip = chips[chipId];
        chip.setChipOption();
    }

    public void nes_set_mute_mask(byte chipId, int muteMask) {
        Nes chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    public void setVolumeAPU(int db) {
        for (Nes info : chips) info.setVolumeAPU(db);
    }

    public void setVolumeDMC(int db) {
        for (Nes info : chips) info.setVolumeDMC(db);
    }

    public void setVolumeFDS(int db) {
        for (Nes info : chips) info.setVolumeFDS(db);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        nes_w(chipId, adr, (byte) data);
        return 0;
    }

    //----

//    public static class APU extends IntFNesInst {};
    public static class DMC extends IntFNesInst {
        @Override
        public Map<String, Integer> getVisVolume() {
            Map<String, Integer> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("DMC", vol != 0 ? vol : MDSound.np_nes_dmc_volume);
            return result;
        }
    }
    public static class FDS extends IntFNesInst {
        @Override
        public Map<String, Integer> getVisVolume() {
            Map<String, Integer> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("FDS", vol != 0 ? vol : MDSound.np_nes_fds_volume);
            return result;
        }
    }
    public static class MMC5 extends IntFNesInst {
        @Override
        public Map<String, Integer> getVisVolume() {
            Map<String, Integer> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("MMC5", vol != 0 ? vol : MDSound.np_nes_mmc5_volume);
            return result;
        }
    }
    public static class N160 extends IntFNesInst {
        @Override
        public Map<String, Integer> getVisVolume() {
            Map<String, Integer> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("N160", vol != 0 ? vol : MDSound.np_nes_n106_volume);
            return result;
        }
    }
    public static class VRC6 extends IntFNesInst {
        @Override
        public Map<String, Integer> getVisVolume() {
            Map<String, Integer> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("Vrc6Inst", vol != 0 ? vol : MDSound.np_nes_vrc6_volume);
            return result;
        }
    }
    public static class VRC7 extends IntFNesInst {
        @Override
        public Map<String, Integer> getVisVolume() {
            Map<String, Integer> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("VRC7", vol != 0 ? vol : MDSound.np_nes_vrc7_volume);
            return result;
        }
    }
    public static class FME7 extends IntFNesInst {
        @Override
        public Map<String, Integer> getVisVolume() {
            Map<String, Integer> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("FME7", vol != 0 ? vol : MDSound.np_nes_fme7_volume);
            return result;
        }
    }


    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
        result.put("APU", vol != 0 ? vol : MDSound.np_nes_apu_volume);
        return result;
    }
}
