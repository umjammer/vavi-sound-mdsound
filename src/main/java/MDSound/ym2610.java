package MDSound;
import MDSound.fmgen.opna.OPNB;

    public class ym2610 extends Instrument
    {
        private OPNB[] chip = new OPNB[2];
        private final int DefaultYM2610ClockValue = 8000000;

        @Override public String getName() { return "YM2610"; }
        @Override public String getShortName()  { return "OPNB"; }

        public ym2610()
        {
            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 }, new int[] { 0, 0 }, new int[] { 0, 0 } , new int[] { 0, 0 } , new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 }, new int[] { 0, 0 }, new int[] { 0, 0 } , new int[] { 0, 0 } , new int[] { 0, 0 } }
            };
            //0..Main 1..FM 2..SSG 3..PCMa 4..PCMb
        }

        @Override public void Reset(byte ChipID)
        {
            if (chip[ChipID] == null) return;
            chip[ChipID].Reset();
        }

        @Override public int Start(byte ChipID, int clock)
        {
            chip[ChipID] = new OPNB();
            chip[ChipID].Init(DefaultYM2610ClockValue, clock);

            return clock;
        }

        @Override public int Start(byte ChipID, int clock, int FMClockValue, Object... option)
        {
            chip[ChipID] = new OPNB();
            chip[ChipID].Init(FMClockValue, clock,false, new byte[0x20ffff], 0x20ffff, new byte[0x20ffff], 0x20ffff);

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
            visVolume[ChipID][1][0] = chip[ChipID].visVolume[0];
            visVolume[ChipID][1][1] = chip[ChipID].visVolume[1];
            visVolume[ChipID][2][0] = chip[ChipID].psg.visVolume;
            visVolume[ChipID][2][1] = chip[ChipID].psg.visVolume;
            visVolume[ChipID][3][0] = chip[ChipID].visRtmVolume[0];
            visVolume[ChipID][3][1] = chip[ChipID].visRtmVolume[1];
            visVolume[ChipID][4][0] = chip[ChipID].visAPCMVolume[0];
            visVolume[ChipID][4][1] = chip[ChipID].visAPCMVolume[1];
        }

        private int YM2610_Write(byte ChipID, int adr, byte data)
        {
            if (chip[ChipID] == null) return 0;
            chip[ChipID].SetReg(adr, data);
            return 0;
        }

        public void YM2610_setAdpcmA(byte ChipID, byte[] _adpcma, int _adpcma_size)
        {
            if (chip[ChipID] == null) return;
            chip[ChipID].setAdpcmA(_adpcma, _adpcma_size);
        }

        public void YM2610_setAdpcmB(byte ChipID, byte[] _adpcmb, int _adpcmb_size)
        {
            if (chip[ChipID] == null) return;
            chip[ChipID].setAdpcmB(_adpcmb, _adpcmb_size);
        }

        public void SetFMVolume(byte ChipID, int db)
        {
            if (chip[ChipID] == null) return;

            chip[ChipID].SetVolumeFM(db);
        }

        public void SetPSGVolume(byte ChipID, int db)
        {
            if (chip[ChipID] == null) return;

            chip[ChipID].SetVolumePSG(db);
        }

        public void SetAdpcmAVolume(byte ChipID, int db)
        {
            if (chip[ChipID] == null) return;

            chip[ChipID].SetVolumeADPCMATotal(db);
        }

        public void SetAdpcmBVolume(byte ChipID, int db)
        {
            if (chip[ChipID] == null) return;

            chip[ChipID].SetVolumeADPCMB(db);
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            return YM2610_Write(ChipID, (int)adr, (byte)data);
        }
    }

