package mdsound.instrument;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import vavi.sound.ymfm.Opn.Ym2608;
import vavi.sound.ymfm.YmFm.VgmChip;

import static java.lang.System.getLogger;
import static vavi.sound.ymfm.YmFm.AccessClass.ADPCM_A;


public class YmFmYm2608Inst extends Instrument.BaseInstrument {

    private static final Logger logger = getLogger(Ym2608Inst.class.getName());

    private static final int DefaultYM2608ClockValue = 8000000;
    private final VgmChip[] chip = new VgmChip[2];

    // TODO similar variables in VgmChip class, those can be eliminated?
    long output_pos;
    long output_step;

    public YmFmYm2608Inst() {
        //0..Main 1..FM 2..SSG 3..Rhm 4..PCM
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}},
                new int[][] {new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}, new int[] {0, 0}}
        };
    }

    @Override
    public String getName() {
        return "YM2608ymfm";
    }

    @Override
    public String getShortName() {
        return "OPNA";
    }

    private void load(int chipId) {
try {
        Path rom = Path.of(System.getProperty("mdsound.pcm.path", ""), "ym2608_adpcm_rom.bin");
        if (!Files.exists(rom))
            logger.log(Level.WARNING, "YM2608 enabled but ym2608_adpcm_rom.bin not found: " + rom);
        else {
            byte[] temp;
            try {
                temp = Files.readAllBytes(rom);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            chip[chipId].write_data(ADPCM_A, 0, temp.length, temp, 0);
logger.log(Level.TRACE, rom + " loaded");
        }
} catch (Throwable t) {
 logger.log(Level.ERROR, t.getMessage(), t);
 throw t;
}
    }

    @Override
    public void reset(int chipId) {
        chip[chipId].reset();

        output_pos = 0;
    }

    @Override
    public int start(int chipId, int samplingRate) {
        chip[chipId] = new VgmChip(DefaultYM2608ClockValue, Ym2608.class);
        load(chipId);

        output_step = 0x1_0000_0000L / samplingRate;

        return samplingRate;
    }

    /**
     * @param option String: Path of rhythm sound file or Function<String, Stream>:
     */
    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chip[chipId] = new VgmChip(clock, Ym2608.class);
        load(chipId);

        output_step = 0x1_0000_0000L / samplingRate;

        return samplingRate;
    }

    @Override
    public void stop(int chipId) {
        chip[chipId] = null;
    }

    int[] buffer = new int[2];

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        buffer[0] = 0;
        buffer[1] = 0;
        chip[chipId].generate(output_pos, output_step, buffer);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
//            logger.log(Level.TRACE, "[%8d] : [%8d] [%d]\r".formatted(outputs[0][i], outputs[1][i],i));
        }

        output_pos += output_step;

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
//        visVolume[chipId][1][0] = chip[chipId].visVolume[0];
//        visVolume[chipId][1][1] = chip[chipId].visVolume[1];
//        visVolume[chipId][2][0] = chip[chipId].psg.visVolume;
//        visVolume[chipId][2][1] = chip[chipId].psg.visVolume;
//        visVolume[chipId][3][0] = chip[chipId].visRtmVolume[0];
//        visVolume[chipId][3][1] = chip[chipId].visRtmVolume[1];
//        visVolume[chipId][4][0] = chip[chipId].visAPCMVolume[0];
//        visVolume[chipId][4][1] = chip[chipId].visAPCMVolume[1];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        if (chip[chipId] == null) return 0;
        chip[chipId].write(port * 0x100 + adr, data);
        return 0;
    }

    public byte[] getADPCMBuffer(int chipId) {
//        return chip[chipId].getADPCMBuffer();
        return null;
    }

    public int readStatusEx(int chipId) {
//        return chip[chipId].readStatusEx();
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x80, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" -> {
                result.put("ym2608", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
                result.put("ym2608FM", getMonoVolume(visVolume[0][1][0], visVolume[0][1][1], visVolume[1][1][0], visVolume[1][1][1]));
                result.put("ym2608SSG", getMonoVolume(visVolume[0][2][0], visVolume[0][2][1], visVolume[1][2][0], visVolume[1][2][1]));
                result.put("ym2608Rtm", getMonoVolume(visVolume[0][3][0], visVolume[0][3][1], visVolume[1][3][0], visVolume[1][3][1]));
                result.put("ym2608APCM", getMonoVolume(visVolume[0][4][0], visVolume[0][4][1], visVolume[1][4][0], visVolume[1][4][1]));
            }
        }
        return result;
    }

    // TODO automatic wired, use annotation?
    public void setFMVolume(int vol, double ignored) {
        if (chip[0] == null) return; // chip[0].setFMVolume(vol);
        if (chip[1] == null) return; // chip[1].setFMVolume(vol);
    }

    // TODO automatic wired, use annotation?
    public void setPSGVolume(int vol, double ignored) {
        if (chip[0] == null) return; // chip[0].setPSGVolume(vol);
        if (chip[1] == null) return; // chip[1].setPSGVolume(vol);
    }

    // TODO automatic wired, use annotation?
    public void setRhythmVolume(int vol, double ignored) {
        if (chip[0] == null) return; // chip[0].setRhythmVolume(vol);
        if (chip[1] == null) return; // chip[1].setRhythmVolume(vol);
    }

    // TODO automatic wired, use annotation?
    public void setAdpcmVolume(int vol, double ignored) {
        if (chip[0] == null) return; // chip[0].setAdpcmVolume(vol);
        if (chip[1] == null) return; // chip[1].setAdpcmVolume(vol);
    }
}
