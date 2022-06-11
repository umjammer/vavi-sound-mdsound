package mdsound;

public class SinWave extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "SinWave";
    }

    @Override
    public String getShortName() {
        return "SIN";
    }

    public SinWave() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
        // 0..Main
    }

    @Override
    public void reset(byte chipID) {
        if (chip[chipID] == null) {
            chip[chipID] = new SinWaveGen();
        }
        // chip[chipID].render = false;
    }

    @Override
    public int start(byte chipID, int clock) {
        return start(chipID, clock, DefaultClockValue, null);
    }

    @Override
    public int start(byte chipID, int clock, int clockValue, Object... option) {
        reset(chipID);
        chip[chipID].clock = clock;
        chip[chipID].render = true;

        return clock; // samplingRate
    }

    @Override
    public void stop(byte chipID) {
        if (chip[chipID] == null) return;
        chip[chipID].render = false;
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        if (chip[chipID] == null) return;
        chip[chipID].update(outputs, samples);
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        if (chip[chipID] == null) return 0;
        return chip[chipID].write(data);
    }

    private static final int DefaultClockValue = 0;
    private SinWaveGen[] chip = new SinWaveGen[2];

    private static class SinWaveGen {
        public boolean render = true;

        public double clock = 44100.0;
        public double tone = 440.0;
        public double delta = 0;

        public void update(int[][] outputs, int samples) {
            if (!render) return;

            for (int i = 0; i < samples; i++) {
                double d = (Math.sin(delta * Math.PI / 180.0) * 4000.0);
                int n = (int) d;
                delta += 360.0f * tone / clock;

                outputs[0][i] = n;
                outputs[1][i] = n;
            }
        }

        public int write(int data) {
            render = (data != 0);
            return 0;
        }
    }
}
