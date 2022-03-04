package mdsound;
import java.util.function.Function;

import mdsound.fmgen.opna;
import dotnet4j.io.Stream;

    public class ym2608 extends Instrument
    {
        private opna.OPNA[] chip = new opna.OPNA[2];
        //private fmgen.OPNA2[] chip = new fmgen.OPNA2[2];
        private static final int DefaultYM2608ClockValue = 8000000;

        @Override public String getName ()  { return "YM2608"; }
        @Override public String getShortName () { return "OPNA"; }

        public ym2608()
        {
            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 }, new int[] { 0, 0 }, new int[] { 0, 0 } , new int[] { 0, 0 } , new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 }, new int[] { 0, 0 }, new int[] { 0, 0 } , new int[] { 0, 0 } , new int[] { 0, 0 } }
            };
            //0..Main 1..FM 2..SSG 3..Rhm 4..PCM
        }

        @Override public void Reset(byte ChipID)
        {
            chip[ChipID].Reset();
        }

        @Override public int Start(byte ChipID, int clock)
        {
            chip[ChipID] = new opna.OPNA(ChipID);
            //chip[ChipID] = new fmgen.OPNA2();
            chip[ChipID].Init(DefaultYM2608ClockValue, clock);

            return clock;
        }

        /// <summary>
        ///
        /// </summary>
        /// <param name="ChipID"></param>
        /// <param name="clock"></param>
        /// <param name="FMClockValue"></param>
        /// <param name="option">リズム音ファイルのパス(終端に\をつけること)</param>
        /// <returns></returns>
        @Override public int Start(byte ChipID, int clock, int FMClockValue, Object... option)
        {
            chip[ChipID] = new opna.OPNA(ChipID);
            //chip[ChipID] = new fmgen.OPNA2();
            if (option != null && option.length > 0 && option[0] instanceof Function) // <string, Stream>
            {
                if (option[0] instanceof Function) // <String, Stream>
                    chip[ChipID].Init(FMClockValue, clock, false, (Function<String, Stream>)option[0]);
                else if (option[0] instanceof String)
                    chip[ChipID].Init(FMClockValue, clock, false, (String)option[0]);
            }
            else
            {
                chip[ChipID].Init(FMClockValue, clock);
            }

            return clock;
        }

        @Override public void Stop(byte ChipID)
        {
            chip[ChipID] = null;
        }

        int[] buffer = new int[2];

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
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

        private int YM2608_Write(byte ChipID, int adr, byte data)
        {
            if (chip[ChipID] == null) return 0;

            chip[ChipID].SetReg(adr, data);
            return 0;
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

        public void SetRhythmVolume(byte ChipID, int db)
        {
            if (chip[ChipID] == null) return;

            chip[ChipID].SetVolumeRhythmTotal(db);
        }

        public void SetAdpcmVolume(byte ChipID, int db)
        {
            if (chip[ChipID] == null) return;

            chip[ChipID].SetVolumeADPCM(db);
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            return YM2608_Write(ChipID, (int)adr, (byte)data);
        }

        public byte[] GetADPCMBuffer(byte ChipID)
        {
            return chip[ChipID].GetADPCMBuffer();
        }

        public int ReadStatusEx(byte ChipID)
        {
            return chip[ChipID].ReadStatusEx();
        }
    }
