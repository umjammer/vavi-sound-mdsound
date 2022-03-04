package mdsound.np.chip;

import mdsound.MDSound;
import mdsound.np.IDevice;
import mdsound.np.IDevice.ISoundChip;
import mdsound.np.np_nes_dmc;

    public class nes_dmc implements ISoundChip
    {
        public np_nes_dmc dmc = new np_nes_dmc();
        public np_nes_dmc.NES_DMC chip;

        @Override public boolean Read(int adr, int val, int id/* = 0*/)
        {
            return dmc.NES_DMC_np_Read(chip, adr, val);
        }

        @Override public int Render(int[] b)
        {
            int ret = dmc.NES_DMC_org_Render(chip, b);
            MDSound.np_nes_dmc_volume = Math.abs(b[0]);
            return ret;
        }

        @Override public void Reset()
        {
            dmc.NES_DMC_np_Reset(chip);
        }

        @Override public void SetClock(double clock)
        {
            dmc.NES_DMC_np_SetClock(chip,clock);
        }

        @Override public void SetMask(int mask)
        {
            dmc.NES_DMC_np_SetMask(chip, mask);
        }

        @Override public void SetOption(int id, int val)
        {
            dmc.NES_DMC_np_SetOption(chip, id, val);
        }

        @Override public void SetRate(double rate)
        {
            dmc.NES_DMC_np_SetRate(chip, rate);
        }

        @Override public void SetStereoMix(int trk, short mixl, short mixr)
        {
            dmc.NES_DMC_np_SetStereoMix(chip, trk, mixl, mixr);
        }

        @Override public void Tick(int clocks)
        {
            dmc.org_Tick(chip, clocks);
        }

        @Override public boolean Write(int adr, int val, int id/* = 0*/)
        {
            return dmc.NES_DMC_np_Write(chip, adr, val);
        }

        public void SetMemory(IDevice r)
        {
            dmc.NES_DMC_org_SetMemory(chip, r);
        }
    }
