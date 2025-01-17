package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.fmgen.OPM;
import vavi.sound.ymfm.Opm.Ym2151;
import vavi.sound.ymfm.YmFm.VgmChip;


public class YmFmYm2151Inst extends Instrument.BaseInstrument {

    public static final int DefaultYM2151ClockValue = 3579545;

    private final VgmChip[] chip = new VgmChip[2];

    public YmFmYm2151Inst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public String getName() {
        return "YM2151ymfm";
    }

    @Override
    public String getShortName() {
        return "OPM";
    }

    // TODO similar variables in VgmChip class, those can be eliminated?
    long output_pos;
    long output_step;

    @Override
    public void reset(int chipId) {
        if (chip[chipId] == null) return;
        chip[chipId].reset();

        output_pos = 0;
    }

    @Override
    public int start(int chipId, int samplingRate) {
        chip[chipId] = new VgmChip(DefaultYM2151ClockValue, Ym2151.class);

        output_step = 0x1_0000_0000L / samplingRate;

        return samplingRate;
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chip[chipId] = new VgmChip(clock, Ym2151.class);

        output_step = 0x1_0000_0000L / samplingRate;

        return samplingRate;
    }

    @Override
    public void stop(int chipId) {
        chip[chipId] = null;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        if (chip[chipId] == null) return;

        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chip[chipId].generate(output_pos, output_step, buffer);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
            //logger.log(Level.TRACE, "[%8d] : [%8d] [%d]".formatted(outputs[0][i], outputs[1][i],i));
        }

        output_pos += output_step;

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        if (chip[chipId] == null) return 0;

        chip[chipId].write(adr, data);
        return 0;
    }

    //----

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
        }
        return result;
    }
}
