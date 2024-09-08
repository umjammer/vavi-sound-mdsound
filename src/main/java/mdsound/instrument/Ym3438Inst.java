package mdsound.instrument;

import mdsound.Instrument;
import mdsound.chips.Ym3438Const;
import mdsound.chips.Ym3438;


public class Ym3438Inst extends Instrument.BaseInstrument {

    private final Ym3438[] chips = new Ym3438[] {new Ym3438(), new Ym3438()};

    private Ym3438Const.Type type;

    public void setChipType(Ym3438Const.Type type) {
        this.type = type;
    }

    @Override
    public String getName() {
        return "YM3438";
    }

    @Override
    public String getShortName() {
        return "OPN2cmos";
    }

    private void writeBuffered(int chipId, int port, int data) {
        Ym3438 chip = chips[chipId];
        chip.write(port, data);
    }

    private void generateResampled(int chipId, int[] buf) {
        Ym3438 chip = chips[chipId];
        chip.update(buf);
    }

    private int[] gsBuffer = new int[2];

    private void generateStream(int chipId, int[][] sndPtr, int numSamples) {

        for (int i = 0; i < numSamples; i++) {
            generateResampled(chipId, gsBuffer);
            //smpl[i] = gsBuffer[0];
            //smpr[i] = gsBuffer[1];
            sndPtr[0][i] = gsBuffer[0];
            sndPtr[1][i] = gsBuffer[1];
        }
    }

    public void setMute(int chipId, int mute) {
        chips[chipId].setMuteMask(mute);
    }

    public void setMute(int chipId, int ch, boolean mute) {
        chips[chipId].setMute(ch, mute);
    }

    public Ym3438Inst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, clock, 0);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        chips[chipId].setChipType(type);
        chips[chipId].reset(clock, clockValue);
        return clock;
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].reset(0, 0);
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset(0, 0);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        generateStream(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        writeBuffered(chipId, adr, data);
        return 0;
    }
}

