package mdsound.instrument;

import java.lang.System.Logger;
import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.NukedYmF262;
import mdsound.chips.NukedYmF262.Chip;

import static java.lang.System.getLogger;
import static mdsound.chips.NukedYmF262.RSM_FRAC;


// YmF262 OPL3 nuked TODO WIP
public class NukedYmF262Inst extends Instrument.BaseInstrument {

    private static final Logger logger = getLogger(NukedYmF262Inst.class.getName());

    public static final int MAX_CHIPS = 0x02;

    private final NukedYmF262[] chips = {new NukedYmF262(), new NukedYmF262()};

    private final Chip chip = new Chip();

    public NukedYmF262Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YMF262nuked";
    }

    @Override
    public String getShortName() {
        return "Opl3";
    }

    @Override
    public void reset(int chipId) {
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;

        int rate = clock / 288;
        if ((CHIP_SAMPLING_MODE == 0x01 && rate < CHIP_SAMPLE_RATE) || CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        chips[chipId].OPL3_Reset(chip, samplingRate);

        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        writeInternal(chipId, (port << 1) | 0x00, adr);
        writeInternal(chipId, (port << 1) | 0x01, data);
        return 0;
    }

    private int address;

    private void writeInternal(int chipId, int adr, int data) {
        switch (adr) {
            case 0 -> { this.address = data; }
            case 2 -> { this.address = data | 0x100; }
            case 1, 3 -> { chips[chipId].OPL3_WriteRegBuffered(chip, this.address, data); }
        }
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        short[] b = new short[2];

        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;
        }
        // make sure that the enqueued register writes get processed properly
        if (chip.rateRatio == (1 << RSM_FRAC)) {

            for (int i = 0; i < samples; i++) {
                if ((chip.writeBuf[chip.writeBuf_cur].reg & 0x200) == 0)
                    break;
                chips[chipId].OPL3_Generate4Ch(chip, chip.samples);
            }
        } else {
            chip.sampleCnt += samples << RSM_FRAC;
            while (chip.sampleCnt >= chip.rateRatio) {
                if ((chip.writeBuf[chip.writeBuf_cur].reg & 0x200) == 0) {
                    chip.sampleCnt = 0;
                    break;
                }
                chips[chipId].OPL3_Generate4Ch(chip, chip.samples);
                chip.sampleCnt -= chip.rateRatio;
            }
        }

        for (int i = 0; i < samples; i++) {
            chips[chipId].OPL3_GenerateStream(chip, b, 0, 1);
            outputs[0][i] = b[0];
            outputs[1][i] = b[1];

//logger.log(Level.TRACE, "output %d %d".formatted(outputs[0][0], outputs[1][0]));
            visVolume[chipId][i][0] = outputs[0][i];
            visVolume[chipId][i][1] = outputs[1][i];
        }
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public void setMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(ch); // TODO
    }

    @Override
    public void resetMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(~ch); // TODO
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
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
