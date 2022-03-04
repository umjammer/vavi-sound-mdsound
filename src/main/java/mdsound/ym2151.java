package mdsound;
import mdsound.fmgen.OPM;
import mdsound.fmgen.fmgen;

    public class ym2151 extends Instrument
    {
        private OPM[] chip = new OPM[2];
        private static final int DefaultYM2151ClockValue = 3579545;

        @Override public String getName()  { return "YM2151"; }
        @Override public String getShortName() { return "OPM"; }

        public ym2151()
        {
            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };
        }

        @Override public void Reset(byte ChipID)
        {
            if (chip[ChipID] == null) return;
            chip[ChipID].Reset();
        }

        @Override public int Start(byte ChipID, int clock)
        {
            chip[ChipID] = new OPM();
            chip[ChipID].Init(DefaultYM2151ClockValue,clock,false);

            return clock;
        }

        @Override public int Start(byte ChipID, int clock, int FMClockValue, Object... option)
        {
            chip[ChipID] = new OPM();
            chip[ChipID].Init(FMClockValue,clock,false);

            return clock;
        }

        @Override public void Stop(byte ChipID)
        {
            chip[ChipID] = null;
        }

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            if (chip[ChipID] == null) return;
            int[] buffer = new int[2];
            buffer[0] = 0;
            buffer[1] = 0;
            chip[ChipID].Mix(buffer, 1);
            for (int i = 0; i < 1; i++)
            {
                outputs[0][i] = buffer[i * 2 + 0];
                outputs[1][i] = buffer[i * 2 + 1];
                //System.err.printf("[{0:d8}] : [{1:d8}] [{2}]\r\n", outputs[0][i], outputs[1][i],i);
            }

            visVolume[ChipID][0][0] = outputs[0][0];
            visVolume[ChipID][0][1] = outputs[1][0];
        }

        private int YM2151_Write(byte ChipID, byte adr, byte data)
        {
            if (chip[ChipID] == null) return 0;

            chip[ChipID].SetReg(adr, data);
            return 0;
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            return YM2151_Write(ChipID, (byte)adr, (byte)data);
        }
    }
