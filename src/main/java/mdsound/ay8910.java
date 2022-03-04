package mdsound;
import mdsound.fmgen.PSG;
import mdsound.fmgen.fmgen;

    public class ay8910 extends Instrument
    {
        private PSG[] chip = new PSG[2];
        private static final int DefaultAY8910ClockValue = 1789750;

        @Override public String getName()  { return "AY8910"; }
        @Override public String getShortName() { return "AY10"; }

        public ay8910()
        {
            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };
            //0..Main
        }

        @Override public void Reset(byte ChipID)
        {
            if (chip[ChipID] == null) return;
            chip[ChipID].Reset();
        }

        @Override public int Start(byte ChipID, int clock)
        {
            chip[ChipID] = new PSG();
            chip[ChipID].SetClock((int)DefaultAY8910ClockValue, (int)clock);

            return clock;
        }

        @Override public int Start(byte ChipID, int clock, int PSGClockValue, Object... option)
        {
            chip[ChipID] = new PSG();
            chip[ChipID].SetClock((int)PSGClockValue, (int)clock);

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

        private int AY8910_Write(byte ChipID, byte adr, byte data)
        {
            if (chip[ChipID] == null) return 0;
            chip[ChipID].SetReg(adr, data);
            return 0;
        }

        public void AY8910_SetMute(byte ChipID, int val)
        {
            PSG PSG = chip[ChipID];
            if (PSG == null) return;


            PSG.SetChannelMask(val);

        }

        public void SetVolume(byte ChipID, int db)
        {
            if (chip[ChipID] == null) return;

            chip[ChipID].SetVolume(db);
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            if (chip[ChipID] == null) return 0;
            chip[ChipID].SetReg((int)adr, (byte)data);
            return 0;
        }
    }

