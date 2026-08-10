package mdsound.instrument;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.Nes;
import mdsound.np.NpNesFds;

import static mdsound.MDSound.Chip.MAIN_TAG;


// nes for vgm
public class NesInst extends Instrument.BaseInstrument implements Instrument.PcmEnabled {

    private static final Logger logger = System.getLogger(NesInst.class.getName());

    private static final byte MAX_CHIPS = 0x02;

    final Nes[] chips = {new Nes(), new Nes()};

    private final int[] mask = {0, 0};

    public NesInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "NesAPU";
    }

    @Override
    public String getShortName() {
        return "NES";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... Option) {
        assert chipId < MAX_CHIPS;

        int rate = clock / 4;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) || CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        chips[chipId].start(clock, rate);
        chips[chipId].setListener(listener);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
//logger.log(Level.TRACE, "adr: %02x, %02x".formatted(adr, data));
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

    @Override
    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= 0x1 << ch;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~(0x1 << ch);
        chips[chipId].setMuteMask(mask[chipId]);
    }

    public void setEmuCore(int emulator) {
    }

    public void setChipOption(int chipId) {
        chips[chipId].setChipOption();
    }

    // ----

    public synchronized void setFDSMask(int chipId) {
        mask[chipId] |= 0x20;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    public synchronized void resetFDSMask(int chipId) {
        mask[chipId] &= ~0x20;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    @Override
    public void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int dataStart = (int) extras[0];
logger.log(Level.DEBUG, "PCM: offset: " + offset + ", length: " + length + ", dataStart: " + dataStart);
        chips[chipId].writeRam(dataStart, length, buf, offset);
    }

    public synchronized int[] readApu(int chipId) {
        return chips[chipId].readApu().reg;
    }

    public synchronized int[] readDmc(int chipId) {
        return chips[chipId].readDmc().reg;
    }

    private synchronized Map<String, Object> getInfo(int chipId) {
        return toInfo(chips[chipId].readFds());
    }

    public static Map<String, Object> toInfo(NpNesFds fds) { // TODO public
        Map<String, Object> info = new HashMap<>();
        info.put("freq", fds.lastFreq);
        info.put("vol", fds.lastVol);
        info.put("wave", fds.wave);

        info.put("VolDir", fds.envMode[1]);
        info.put("VolSpd", fds.envSpeed[1]);
        info.put("VolGain", fds.envOut[1]);
        info.put("VolDi", fds.envHalt);
        info.put("VolFrq", fds.freq[1]);
        info.put("VolHlR", fds.wavHalt);

        info.put("ModDir", fds.envMode[0]);
        info.put("ModSpd", fds.envSpeed[0]);
        info.put("ModGain", fds.envOut[0]);
        info.put("ModDi", fds.modHalt);
        info.put("ModFrq", fds.freq[0]);
        info.put("ModCnt", fds.modPos);

        info.put("EnvSpd", (int) fds.masterEnvSpeed);
        info.put("EnvVolSw", !fds.envDisable[1]);
        info.put("EnvModSw", !fds.envDisable[0]);

        info.put("MasterVol", fds.masterVol);
        info.put("WE", fds.wavWrite);

        return info;
    }

    public void setVolume(String tag, int vol, double ignored) {
        for (Nes chip : chips) {
logger.log(Level.DEBUG, "tag: " + tag + ", vol: " + vol);
            switch (tag) {
                case MAIN_TAG -> chip.setVolumeAPU(vol);
                case "DMC" -> chip.setVolumeDMC(vol);
                case "FDS" -> chip.setVolumeFDS(vol);
            }
        }
    }

    // ----

    private final Consumer<int[]> listener = ds -> {
        if (ds[0] != -1) np_nes_apu_volume = ds[0];
        if (ds[1] != -1) np_nes_dmc_volume = ds[1];
        if (ds[2] != -1) np_nes_fds_volume = ds[2];
    };

    private int np_nes_apu_volume;
    int np_nes_dmc_volume;
    int np_nes_fds_volume;

    // vgm
    public static class DmcInst extends NesInst {
        @Override
        public String getName() {
            return "NesDMC";
        }

        @Override
        public String getShortName() {
            return "DMC";
        }

        @Override
        public Map<String, Object> getView(int chipId, String key, Object... args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_dmc_volume);
            return result;
        }

        private void setDMCVolume(int db) {
            for (Nes chip : chips) chip.setVolumeDMC(db);
        }

        // TODO automatic wired, use annotation?
        public void setVolume(int vol, double ignored) {
            setDMCVolume(vol);
        }
    }

    // vgm
    public static class FdsInst extends NesInst {
        @Override
        public String getName() {
            return "NesFDS";
        }

        @Override
        public String getShortName() {
            return "FDS";
        }

        @Override
        public Map<String, Object> getView(int chipId, String key, Object... args) {
            Map<String, Object> result = new HashMap<>();
            int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
            result.put("volume", vol != 0 ? vol : np_nes_fds_volume);
            return result;
        }

        private void setFDSVolume(int db) {
            for (Nes chip : chips) chip.setVolumeFDS(db);
        }

        // TODO automatic wired, use annotation?
        public void setVolume(int vol, double ignored) {
            setFDSVolume(vol);
        }
    }

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" -> {
                int vol = getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]);
                result.put("APU", vol != 0 ? vol : np_nes_apu_volume);
            }
            case "info" -> result.put(getName(), getInfo(chipId));
        }
        return result;
    }
}
