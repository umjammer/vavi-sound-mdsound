package mdsound;

import mdsound.fmgen.OPM;


public class Ym2151 extends Instrument.BaseInstrument {

    private OPM[] chip = new OPM[2];
    private static final int DefaultYM2151ClockValue = 3579545;

    @Override
    public String getName() {
        return "YM2151";
    }

    @Override
    public String getShortName() {
        return "OPM";
    }

    public Ym2151() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void reset(byte chipId) {
        if (chip[chipId] == null) return;
        chip[chipId].reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        chip[chipId] = new OPM();
        chip[chipId].init(DefaultYM2151ClockValue, clock, false);

        return clock;
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        chip[chipId] = new OPM();
        chip[chipId].init(clockValue, clock, false);

        return clock;
    }

    @Override
    public void stop(byte chipId) {
        chip[chipId] = null;
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        if (chip[chipId] == null) return;
        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chip[chipId].mix(buffer, 1);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
            //System.err.printf("[%8d] : [%8d] [%d]\n", outputs[0][i], outputs[1][i],i);
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private int write(byte chipId, byte adr, byte data) {
        if (chip[chipId] == null) return 0;

        chip[chipId].setReg(adr, data);
        return 0;
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        return write(chipId, (byte) adr, (byte) data);
    }
}
