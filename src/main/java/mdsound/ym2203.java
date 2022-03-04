package mdsound;

import mdsound.fmgen.fmgen;
import mdsound.fmgen.opna;

    public class ym2203 extends Instrument
    {
        private opna.OPN[] chip = new opna.OPN[2];
        private static final int DefaultYM2203ClockValue = 3000000;

        @Override public String getName ()  { return "YM2203"; }
        @Override public String getShortName () { return "OPN"; }

        public ym2203()
        {
            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 }, new int[] { 0, 0 }, new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 }, new int[] { 0, 0 }, new int[] { 0, 0 } }
            };
            //0..Main 1..FM 2..SSG
        }

        @Override public void Reset(byte ChipID)
        {
            if (chip[ChipID] == null) return;
            chip[ChipID].Reset();
        }

        @Override public int Start(byte ChipID, int clock)
        {
            chip[ChipID] = new opna.OPN();
            chip[ChipID].Init(DefaultYM2203ClockValue, clock);

            return clock;
        }

        @Override public int Start(byte ChipID, int clock, int FMClockValue, Object... option)
        {
            chip[ChipID] = new opna.OPN();
            chip[ChipID].Init(FMClockValue, clock);

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
            }

            visVolume[ChipID][0][0] = outputs[0][0];
            visVolume[ChipID][0][1] = outputs[1][0];
            visVolume[ChipID][1][0] = chip[ChipID].visVolume[0];
            visVolume[ChipID][1][1] = chip[ChipID].visVolume[1];
            visVolume[ChipID][2][0] = chip[ChipID].psg.visVolume;
            visVolume[ChipID][2][1] = chip[ChipID].psg.visVolume;
        }

        private int YM2203_Write(byte ChipID, byte adr, byte data)
        {
            if (chip[ChipID] == null) return 0;
            chip[ChipID].SetReg(adr, data);
            return 0;
        }

        public void YM2203_SetMute(byte ChipID, int val)
        {
            opna.OPN YM2203 = chip[ChipID];
            if (YM2203 == null) return;


            YM2203.SetChannelMask((int)val);

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

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            return YM2203_Write(ChipID, (byte)adr, (byte)data);
        }
    }
