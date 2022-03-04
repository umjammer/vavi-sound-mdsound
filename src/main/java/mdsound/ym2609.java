package mdsound;

import java.util.function.Function;

import mdsound.fmvgen.OPNA2;
import mdsound.fmvgen.fmvgen;
import mdsound.fmvgen.effect.Compressor;
import mdsound.fmvgen.effect.HPFLPF;
import mdsound.fmvgen.effect.ReversePhase;
import mdsound.fmvgen.effect.chorus;
import mdsound.fmvgen.effect.distortion;
import mdsound.fmvgen.effect.eq3band;
import mdsound.fmvgen.effect.reverb;
import dotnet4j.io.Stream;


    public class ym2609 extends Instrument
    {
        private OPNA2[] chip = new OPNA2[2];
        private static final int DefaultYM2609ClockValue = 8000000;
        private reverb[] reverb = new reverb[2];
        private distortion[] distortion = new distortion[2];
        private chorus[] chorus = new chorus[2];
        private eq3band[] ep3band = new eq3band[2];
        private HPFLPF[] hpflpf = new HPFLPF[2];
        private ReversePhase[] reversePhase = new ReversePhase[2];
        private Compressor[] compressor = new Compressor[2];
        private static final int MaxCh= 39;
        private int[] updateBuffer = new int[2];

        @Override public String getName() { return "YM2609"; }
        @Override public String getShortName() { return "OPNA2"; }

        boolean visVol;
        public boolean getVisVol() { return visVol; }


        public ym2609()
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
            reverb[ChipID] = new reverb((int)clock, MaxCh);
            distortion[ChipID] = new distortion((int)clock, MaxCh);
            chorus[ChipID] = new chorus((int)clock, MaxCh);
            ep3band[ChipID] = new eq3band((int)clock);
            hpflpf[ChipID] = new HPFLPF((int)clock, MaxCh);
            reversePhase[ChipID] = new ReversePhase();
            compressor[ChipID] = new Compressor((int)clock, MaxCh);
            chip[ChipID] = new OPNA2(reverb[ChipID], distortion[ChipID], chorus[ChipID], ep3band[ChipID], hpflpf[ChipID], reversePhase[ChipID], compressor[ChipID]);
            chip[ChipID].Init(DefaultYM2609ClockValue, clock);

            return clock;
        }

        @Override public int Start(byte ChipID, int clock, int FMClockValue, Object... option)
        {
            reverb[ChipID] = new reverb((int)clock, MaxCh);
            distortion[ChipID] = new distortion((int)clock, MaxCh);
            chorus[ChipID] = new chorus((int)clock, MaxCh);
            ep3band[ChipID] = new eq3band((int)clock);
            hpflpf[ChipID] = new HPFLPF((int)clock, MaxCh);
            reversePhase[ChipID] = new ReversePhase();
            compressor[ChipID] = new Compressor((int)clock, MaxCh);
            chip[ChipID] = new OPNA2(reverb[ChipID], distortion[ChipID], chorus[ChipID], ep3band[ChipID], hpflpf[ChipID], reversePhase[ChipID], compressor[ChipID]);

            if (option != null && option.length > 0 && option[0] instanceof Function) //<String, Stream>
            {
                if (option[0] instanceof Function) // <String, Stream>
                    chip[ChipID].Init(FMClockValue, clock, false, (Function<String, Stream>)option[0], null, 0);
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

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            updateBuffer[0] = reverb[ChipID].GetDataFromPosL()>>1;
            updateBuffer[1] = reverb[ChipID].GetDataFromPosR()>>1;

            reverb[ChipID].StoreDataC(reverb[ChipID].GetDataFromPosL() >> 1, reverb[ChipID].GetDataFromPosR() >> 1);
            reverb[ChipID].ClearDataAtPos();

            chip[ChipID].Mix(updateBuffer, 1);
            //for (int i = 0; i < 1; i++)
            //{
            //    outputs[0][i] = updateBuffer[i * 2 + 0];
            //    outputs[1][i] = updateBuffer[i * 2 + 1];

            //    //rev[ChipID].StoreData(0, (outputs[0][i] + outputs[1][i]) / 2);
            //}
            outputs[0][0] = updateBuffer[0];
            outputs[1][0] = updateBuffer[1];

            reverb[ChipID].UpdatePos();

            if (!visVol) return;

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

        public int YM2609_Write(byte ChipID, int adr, byte data)
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

        public void SetAdpcmA(byte ChipID, byte[] _adpcma, int _adpcma_size)
        {
            if (chip[ChipID] == null) return;
            chip[ChipID].setAdpcmA(_adpcma, _adpcma_size);
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            return YM2609_Write(ChipID, (int)adr, (byte)data);
        }
    }
