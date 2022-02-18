package MDSound;
import MDSound.mame.fm;
import MDSound.mame.fm2612;

    public class ym2612mame extends Instrument
    {
        private final int MAX_CHIPS = 2;
        private final int DefaultFMClockValue = 7670454;
        public fm2612[] YM2612_Chip = new fm2612[] { null, null };
        private fm2612.YM2612[] ym2612 = new fm2612.YM2612[MAX_CHIPS];

        @Override public String getName()  { return "YM2612mame"; }
        @Override public String getShortName() { return "OPN2mame"; }

        public ym2612mame()
        {
            visVolume = new int[][][] {
                new int[][] { new int[] { 0, 0 } }
                , new int[][] { new int[] { 0, 0 } }
            };
            //0..Main
        }

        @Override public void Reset(byte ChipID)
        {
            if (YM2612_Chip[ChipID] == null) return;
            //if (ym2612[ChipID] == null) return;

            YM2612_Chip[ChipID].ym2612_reset_chip(ym2612[ChipID]);
        }

        @Override public int Start(byte ChipID, int clock)
        {
            YM2612_Chip[ChipID] = new fm2612();
            ym2612[ChipID] = YM2612_Chip[ChipID].ym2612_init(new fm2612.YM2612(), (int)DefaultFMClockValue, (int)clock, null, null);

            return clock;
        }

        @Override public int Start(byte ChipID, int clock, int ClockValue, Object... option)
        {
            YM2612_Chip[ChipID] = new fm2612();
            ym2612[ChipID] = YM2612_Chip[ChipID].ym2612_init(new fm2612.YM2612(), (int)ClockValue, (int)clock, null, null);
            YM2612_Chip[ChipID].ym2612_update_request = this::ym2612_update_request;

            return clock;
        }

        void ym2612_update_request(byte ChipID, fm.FM_base param)
        {
            YM2612_Chip[ChipID].ym2612_update_one((fm.FM_base)ym2612[ChipID], new int[2][], 0);
        }

        @Override public void Stop(byte ChipID)
        {
            YM2612_Chip[ChipID] = null;
        }

        @Override public void Update(byte ChipID, int[][] outputs, int samples)
        {
            YM2612_Chip[ChipID].ym2612_update_one(ym2612[ChipID], outputs, samples);

            visVolume[ChipID][0][0] = outputs[0][0];
            visVolume[ChipID][0][1] = outputs[1][0];
        }

        @Override public int Write(byte ChipID, int port, int adr, int data)
        {
            if (YM2612_Chip[ChipID] == null) return 0;

            return YM2612_Chip[ChipID].ym2612_write(ChipID, ym2612[ChipID], (byte)adr, (byte)data);
        }

        public void SetMute(byte ChipID, int mask)
        {
            if (YM2612_Chip[ChipID] == null) return;
            YM2612_Chip[ChipID].ym2612_set_mutemask(ChipID, ym2612[ChipID], mask);
        }
    }
