﻿using System;
using System.Collections.Generic;
using System.Text;

namespace MDSound
{
    public class ym2612X : ym2612
    {
        public XGMFunction XGMfunction = new XGMFunction();

        public override void Reset(byte ChipID)
        {
            XGMfunction.Reset(ChipID, YM2612_Chip[ChipID].Rate);
            base.Reset(ChipID);
        }

        public override int Write(byte ChipID, int port, int adr, int data)
        {
            XGMfunction.Write(ChipID, port, adr, data);
            return base.Write(ChipID, port, (byte)adr, (byte)data);
        }

        public override void Update(byte ChipID, int[][] outputs, int samples)
        {
            XGMfunction.Update(ChipID, samples, Write);
            base.Update(ChipID, outputs, samples);
        }


    }
}
