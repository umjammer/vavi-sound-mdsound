package mdsound.np.chip;

import mdsound.MDSound;
import mdsound.np.IDevice.ISoundChip;
import mdsound.np.np_nes_fds;

    public class nes_fds implements ISoundChip
    {
        public np_nes_fds fds = new np_nes_fds();
        public np_nes_fds.NES_FDS chip;

        @Override public boolean Read(int adr, int val, int id/* = 0*/)
        {
            return fds.NES_FDS_Read(chip, adr, val);
        }

        @Override public int Render(int[] b)
        {
            int ret = fds.NES_FDS_org_Render(chip, b);
            MDSound.np_nes_fds_volume = Math.abs(b[0]);
            return ret;
        }

        @Override public void Reset()
        {
            fds.NES_FDS_Reset(chip);
        }

        @Override public void SetClock(double clock)
        {
            fds.NES_FDS_SetClock(chip, clock);
        }

        @Override public void SetMask(int mask)
        {
            fds.NES_FDS_SetMask(chip, mask);
        }

        @Override public void SetOption(int id, int val)
        {
            fds.NES_FDS_SetOption(chip, id, val);
        }

        @Override public void SetRate(double rate)
        {
            fds.NES_FDS_SetRate(chip, rate);
        }

        @Override public void SetStereoMix(int trk, short mixl, short mixr)
        {
            fds.NES_FDS_SetStereoMix(chip, trk, mixl, mixr);
        }

        @Override public void Tick(int clocks)
        {
            fds.Tick(chip, clocks);
        }

        @Override public boolean Write(int adr, int val, int id/* = 0*/)
        {
            return fds.NES_FDS_Write(chip, adr, val);
        }
    }
