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
    public void reset(byte chipID) {
        if (chip[chipID] == null) return;
        chip[chipID].reset();
    }

    @Override
    public int start(byte chipID, int clock) {
        chip[chipID] = new OPM();
        chip[chipID].init(DefaultYM2151ClockValue, clock, false);

        return clock;
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        chip[chipID] = new OPM();
        chip[chipID].init(clockValue, clock, false);

        return clock;
    }

    @Override
    public void stop(byte chipID) {
        chip[chipID] = null;
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        if (chip[chipID] == null) return;
        int[] buffer = new int[2];
        buffer[0] = 0;
        buffer[1] = 0;
        chip[chipID].mix(buffer, 1);
        for (int i = 0; i < 1; i++) {
            outputs[0][i] = buffer[i * 2 + 0];
            outputs[1][i] = buffer[i * 2 + 1];
            //System.err.printf("[%8d] : [%8d] [%d]\n", outputs[0][i], outputs[1][i],i);
        }

        visVolume[chipID][0][0] = outputs[0][0];
        visVolume[chipID][0][1] = outputs[1][0];
    }

    private int write(byte chipID, byte adr, byte data) {
        if (chip[chipID] == null) return 0;

        chip[chipID].setReg(adr, data);
        return 0;
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        return write(chipID, (byte) adr, (byte) data);
    }
}
