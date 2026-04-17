package mdsound.instrument;

import java.lang.System.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;


// nes for nsf
public class NpNesInst extends Instrument.BaseInstrument implements Instrument.PcmEnabled {

    private static final Logger logger = System.getLogger(NpNesInst.class.getName());

    public static final byte MAX_CHIPS = 0x02;

    private final int[] mask = new int[] {0, 0};

    public NpNesInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};

        this.np_nes_apu_volume = 0;
        this.np_nes_dmc_volume = 0;
        this.np_nes_fds_volume = 0;
        this.np_nes_fme7_volume = 0;
        this.np_nes_mmc5_volume = 0;
        this.np_nes_n106_volume = 0;
        this.np_nes_vrc6_volume = 0;
        this.np_nes_vrc7_volume = 0;
    }

    @Override
    public String getName() {
        return "NpNesapu";
    }

    @Override
    public String getShortName() {
        return "NES";
    }

    @Override
    public void reset(int chipId) {
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... Option) {
        assert chipId < MAX_CHIPS;

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
//logger.log(Level.TRACE, "adr: %02x, %02x".formatted(adr, data));
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public synchronized void setMask(int chipId, int ch) {
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
    }

    // ----

    @Override
    public void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
    }

    public void setVolume(int vol, double ignored) {
        np_nes_apu_volume = vol;
    }

    // ----

    private final Consumer<int[]> listener = ds -> {
        if (ds[0] != -1) np_nes_apu_volume = ds[0];
        if (ds[1] != -1) np_nes_dmc_volume = ds[1];
        if (ds[2] != -1) np_nes_fds_volume = ds[2];
        if (ds[3] != -1) np_nes_fme7_volume = ds[3];
        if (ds[4] != -1) np_nes_mmc5_volume = ds[4];
        if (ds[5] != -1) np_nes_n106_volume = ds[5];
        if (ds[6] != -1) np_nes_vrc6_volume = ds[6];
        if (ds[7] != -1) np_nes_vrc7_volume = ds[7];
    };

    public int np_nes_apu_volume;
    public int np_nes_dmc_volume;
    public int np_nes_fds_volume;
    public int np_nes_fme7_volume;
    public int np_nes_mmc5_volume;
    public int np_nes_n106_volume;
    public int np_nes_vrc6_volume;
    public int np_nes_vrc7_volume;

    // vgm
    public static class DmcInst extends NpNesInst {
        @Override
        public String getName() {
            return "NpNesDMC";
        }

        @Override
        public String getShortName() {
            return "DMC";
        }

        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_dmc_volume);
            return result;
        }

        // TODO automatic wired, use annotation?
        public void setVolume(int vol, double ignored) {
            np_nes_dmc_volume = vol;
        }
    }

    // vgm
    public static class FdsInst extends NpNesInst {
        @Override
        public String getName() {
            return "NpNesFDS";
        }

        @Override
        public String getShortName() {
            return "FDS";
        }

        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_fds_volume);
            return result;
        }

        // TODO automatic wired, use annotation?
        public void setVolume(int vol, double ignored) {
            np_nes_fds_volume = vol;
        }
    }

    public static class Mmc5Inst extends NpNesInst {
        @Override
        public String getName() {
            return "NpNesMMC5";
        }

        @Override
        public String getShortName() {
            return "MMC5";
        }

        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_mmc5_volume);
            return result;
        }
    }

    public static class N160Inst extends NpNesInst {
        @Override
        public String getName() {
            return "NpNesN160";
        }

        @Override
        public String getShortName() {
            return "N160";
        }

        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_n106_volume);
            return result;
        }
    }

    public static class Vrc6Inst extends NpNesInst {
        @Override
        public String getName() {
            return "NpNesVRC6";
        }

        @Override
        public String getShortName() {
            return "VRC6";
        }

        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_vrc6_volume);
            return result;
        }
    }

    public static class Vrc7Inst extends NpNesInst {
        @Override
        public String getName() {
            return "NpNesVRC7";
        }

        @Override
        public String getShortName() {
            return "VRC7";
        }

        @Override
        public Map<String, Object> getView(String key, Map<String, Object> args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_vrc7_volume);
            return result;
        }
    }

    public static class Fme7Inst extends NpNesInst {
        @Override
        public String getName() {
            return "NpNesMMC5";
        }

        @Override
        public String getShortName() {
            return "MMC5";
        }

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
}
