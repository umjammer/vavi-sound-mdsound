package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Nes;
import mdsound.np.NpNesFds;


public class IntFNesInst extends Instrument.BaseInstrument {

    private static final byte MAX_CHIPS = 0x02;
    protected Nes[] chips = new Nes[] {new Nes(), new Nes()};

    public IntFNesInst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public String getName() {
        return "NES";
    }

    @Override
    public String getShortName() {
        return "NES";
    }

    @Override
    public void reset(int chipId) {
        Nes chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;
        Nes info = chips[chipId];

        int rate = clock / 4;
        if ((BaseInstrument.CHIP_SAMPLING_MODE == 0x01 && rate < BaseInstrument.CHIP_SAMPLE_RATE) ||
                BaseInstrument.CHIP_SAMPLING_MODE == 0x02)
            rate = BaseInstrument.CHIP_SAMPLE_RATE;
        info.start(clock, rate);
        info.setListener(listenr);
        return rate;
    }

    @Override
    public int start(int chipId, int samplingRate, int clockValue, Object... Option) {
        return start(chipId, clockValue);
    }

    @Override
    public void stop(int chipId) {
        Nes info = chips[chipId];
        info.stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        nes_stream_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        Nes chip = chips[chipId];
        chip.write(adr, (byte) data);
        return 0;
    }

    public void nes_stream_update(int chipId, int[][] outputs, int samples) {
        Nes info = chips[chipId];
        info.update(outputs, samples);
    }

    public void nes_write_ram(int chipId, int dataStart, int dataLength, byte[] ramData) {
        nes_write_ram(chipId, dataStart, dataLength, ramData, 0);
    }

    public void nes_write_ram(int chipId, int dataStart, int dataLength, byte[] ramData, int ramdataStartAdr) {
        Nes chip = chips[chipId];
        chip.writeRam(dataStart, dataLength, ramData, ramdataStartAdr);
    }

    public byte[] nes_r_apu(int chipId) {
        Nes chip = chips[chipId];
        return chip.readApu();
    }

    public byte[] nes_r_dmc(int chipId) {
        Nes chip = chips[chipId];
        return chip.readDmc();
    }

    public NpNesFds nes_r_fds(int chipId) {
        Nes chip = chips[chipId];
        return chip.readDds();
    }

    private void nes_set_emu_core(byte emulator) {
    }

    private void nes_set_chip_option(int chipId) {
        Nes chip = chips[chipId];
        chip.setChipOption();
    }

    public void nes_set_mute_mask(int chipId, int muteMask) {
        Nes chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    private void setAPUVolume(int db) {
        for (Nes info : chips) info.setVolumeAPU(db);
    }

    //----

    private Consumer<int[]> listenr = ds -> {
        if (ds[0] != -1) np_nes_apu_volume = ds[0];
        if (ds[1] != -1) np_nes_dmc_volume = ds[1];
        if (ds[2] != -1) np_nes_fds_volume = ds[2];
        if (ds[3] != -1) np_nes_fme7_volume = ds[3];
        if (ds[4] != -1) np_nes_mmc5_volume = ds[4];
        if (ds[5] != -1) np_nes_n106_volume = ds[5];
        if (ds[6] != -1) np_nes_vrc6_volume = ds[6];
        if (ds[7] != -1) np_nes_vrc7_volume = ds[7];
    };

    public static int np_nes_apu_volume;
    public static int np_nes_dmc_volume;
    public static int np_nes_fds_volume;
    public static int np_nes_fme7_volume;
    public static int np_nes_mmc5_volume;
    public static int np_nes_n106_volume;
    public static int np_nes_vrc6_volume;
    public static int np_nes_vrc7_volume;

    public static class DMC extends IntFNesInst {
        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_dmc_volume);
            return result;
        }
        private void setDMCVolume(int db) {
            for (Nes info : chips) info.setVolumeDMC(db);
        }

        // TODO automatic wired, use annotation?
        public void setVolume(int vol, double ignored) {
            setDMCVolume(vol);
        }
    }
    public static class FDS extends IntFNesInst {
        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_fds_volume);
            return result;
        }
        private void setFDSVolume(int db) {
            for (Nes info : chips) info.setVolumeFDS(db);
        }

        // TODO automatic wired, use annotation?
        public void setVolume(int vol, double ignored) {
            setFDSVolume(vol);
        }
    }
    public static class MMC5 extends IntFNesInst {
        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_mmc5_volume);
            return result;
        }
    }
    public static class N160 extends IntFNesInst {
        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_n106_volume);
            return result;
        }
    }
    public static class VRC6 extends IntFNesInst {
        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_vrc6_volume);
            return result;
        }
    }
    public static class VRC7 extends IntFNesInst {
        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_vrc7_volume);
            return result;
        }
    }
    public static class FME7 extends IntFNesInst {
        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_fme7_volume);
            return result;
        }
    }

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" -> {
                int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
                result.put("APU", vol != 0 ? vol : np_nes_apu_volume);
            }
        }
        return result;
    }

    // TODO automatic wired, use annotation?
    public void setVolume(int vol, double ignored) {
        setAPUVolume(vol);
    }
}
