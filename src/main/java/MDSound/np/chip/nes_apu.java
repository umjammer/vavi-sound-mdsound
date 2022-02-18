package MDSound.np.chip;

import MDSound.MDSound;
import MDSound.np.IDevice.ISoundChip;
import MDSound.np.np_nes_apu;

    public class nes_apu implements  ISoundChip
    {
        public np_nes_apu apu = new np_nes_apu();
        public np_nes_apu.NES_APU chip;

        @Override public boolean Read(int adr, int val, int id /*= 0*/)
        {
            return apu.NES_APU_np_Read(chip, adr, val);
        }

        @Override public int Render(int[] b)
        {
            int ret= apu.NES_APU_org_Render(chip, b);
            MDSound.np_nes_apu_volume = Math.abs(b[0]);
            return ret;
        }

        @Override public void Reset()
        {
            apu.NES_APU_np_Reset(chip);
        }

        @Override public void SetClock(double clock)
        {
            apu.NES_APU_np_SetClock(chip, clock);
        }

        @Override public void SetMask(int mask)
        {
            apu.NES_APU_np_SetMask(chip, mask);
        }

        @Override public void SetOption(int id, int val)
        {
            apu.NES_APU_np_SetOption(chip, id, val);
        }

        @Override public void SetRate(double rate)
        {
            apu.NES_APU_np_SetRate(chip, rate);
        }

        @Override public void SetStereoMix(int trk, short mixl, short mixr)
        {
            apu.NES_APU_np_SetStereoMix(chip, trk, mixl, mixr);
        }

        @Override public void Tick(int clocks)
        {
            apu.Tick(chip, clocks);
        }

        @Override public boolean Write(int adr, int val, int id/* = 0*/)
        {
            return apu.NES_APU_np_Write(chip, adr, val);
        }
    }
