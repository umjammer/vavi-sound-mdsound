package MDSound;

public class SinWave extends Instrument
    {
        @Override public String getName() { return "SinWave"; }
        @Override public String getShortName() { return "SIN"; }

        public SinWave()
        {
            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };
            //0..Main
        }

        @Override public void Reset(byte ChipID)
        {
            if (chip[ChipID] == null)
            {
                chip[ChipID] = new SinWaveGen();
            }
            //chip[ChipID].render = false;
        }

        @Override public int Start(byte ChipID, int clock)
        {
            return Start(ChipID, clock, DefaultClockValue, null);
        }

        @Override public int Start(byte ChipID, int clock, int ClockValue, Object... option)
        {
            Reset(ChipID);
            chip[ChipID].clock = (double)clock;
            chip[ChipID].render = true;

            return clock;//SamplingRate
        }

        @Override public void Stop(byte ChipID)
        {
            if (chip[ChipID] == null) return;
            chip[ChipID].render = false;
        }

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            if (chip[ChipID] == null) return;
            chip[ChipID].Update(outputs, samples);
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            if (chip[ChipID] == null) return 0;
            return chip[ChipID].Write(data);
        }




        private final int DefaultClockValue = 0;
        private SinWaveGen[] chip = new SinWaveGen[2];

        private class SinWaveGen
        {
            public boolean render = true;

            public double clock = 44100.0;
            public double tone = 440.0;
            public double delta = 0;

            public void Update(int[][] outputs, int samples)
            {
                if (!render) return;

                for (int i = 0; i < samples; i++)
                {
                    double d = (Math.sin(delta * Math.PI / 180.0) * 4000.0);
                    int n = (int)d;
                    delta += 360.0f * tone / clock;

                    outputs[0][i] = n;
                    outputs[1][i] = n;
                }
            }

            public int Write(int data)
            {
                render = (data != 0);
                return 0;
            }
        }
    }
