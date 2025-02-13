
package test;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import real.NSoundInterface;
import real.nc86ctl.Nc86ctl;
import real.nc86ctl.Nc86ctl.ChipType;
import real.nc86ctl.Nc86ctl.NIGimic2;
import real.nc86ctl.Nc86ctl.NIRealChip;
import real.nscci.NScci;
import real.nscci.NScci.NSCCI_INTERFACE_INFO;
import real.nscci.NScci.NSCCI_SOUND_CHIP_INFO;
import real.nscci.NScci.NSoundChip;
import test.Common.RealChipType;

import static java.lang.System.getLogger;


public class RealChip {

    private static final Logger logger = getLogger(RealChip.class.getName());

    private NScci nScci;

    private Nc86ctl nc86ctl;

    public RealChip() {
        logger.log(Level.INFO, "RealChip:Ctr:STEP 00(start)");

        int n;
        try {
            nScci = NScci.INSTANCE;
            n = NScci.NSoundInterfaceManager().getInterfaceCount();
            if (n == 0) {
                nScci.Dispose();
                nScci = null;
                logger.log(Level.INFO, "RealChip:Ctr:Not found SCCI.");
            } else {
                logger.log(Level.INFO, "RealChip:Ctr:Found SCCI.(Interface count=%d)".formatted(n));
                getScciInstances();
                NScci.NSoundInterfaceManager().setLevelDisp(false);
            }
        } catch (Exception e) {
            nScci = null;
        }

        logger.log(Level.INFO, "RealChip:Ctr:STEP 01");
        try {
            nc86ctl = Nc86ctl.INSTANCE;
            nc86ctl.initialize();
            n = nc86ctl.getNumberOfChip();
            if (n == 0) {
                nc86ctl.deinitialize();
                nc86ctl = null;
                logger.log(Level.INFO, "RealChip:Ctr:Not found G.I.M.I.C.");
            } else {
                logger.log(Level.INFO, "RealChip:Ctr:Found G.I.M.I.C.(Interface count=%d)".formatted(n));
                NIRealChip nirc = nc86ctl.getChipInterface(0);
                nirc.reset();
            }
        } catch (Exception e) {
            nc86ctl = null;
        }
        logger.log(Level.INFO, "RealChip:Ctr:STEP 02(Success)");
    }

    public void close() {
        if (nScci != null) {
            try {
                nScci.Dispose();
            } catch (Exception e) {
            }
            nScci = null;
        }
        if (nc86ctl != null) {
            try {
                nc86ctl.deinitialize();
            } catch (Exception e) {
            }
            nc86ctl = null;
        }
    }

    public void getScciInstances() {
        int ifc = NScci.NSoundInterfaceManager().getInterfaceCount();

        for (int i = 0; i < ifc; i++) {
            NSoundInterface sif = NScci.NSoundInterfaceManager().getInterface(i);

            int scc = sif.getSoundChipCount();
            for (int j = 0; j < scc; j++) {
                NSoundChip sc = sif.getSoundChip(j);
                NSCCI_SOUND_CHIP_INFO info = sc.getSoundChipInfo();
            }
        }

    }

    public void setLevelDisp(boolean v) {
        if (nScci != null)
            NScci.NSoundInterfaceManager().setLevelDisp(v);
    }

//    public void init() {
//        if (nScci != null) {
//            NScci.NSoundInterfaceManager().init();
//        }
//        if (nc86ctl != null) {
//            nc86ctl.initialize();
//        }
//    }

    public void reset() {
        if (nScci != null)
            NScci.NSoundInterfaceManager().reset();
        if (nc86ctl != null) {
            // nc86ctl.initialize();
            int n = nc86ctl.getNumberOfChip();
            for (int i = 0; i < n; i++) {
                NIRealChip rc = nc86ctl.getChipInterface(i);
                rc.reset();
            }
        }
    }

    public void SendData() {
        if (nScci != null)
            NScci.NSoundInterfaceManager().sendData();
        if (nc86ctl != null) {
//            int n = nc86ctl.getNumberOfChip();
//            for (int i = 0; i < n; i++) {
//                NIRealChip rc = nc86ctl.getChipInterface(i);
//                if (rc != null) {
//                    while ((rc. @in(0x0) &0x00) !=0)
//                    System.Threading.Thread.Sleep(0);
//                }
//            }
        }

        try { Thread.sleep(1000); } catch (InterruptedException ignore) {}
    }

    public void WaitOPNADPCMData(boolean isGIMIC) {
        if (nScci != null)
            NScci.NSoundInterfaceManager().sendData();
        if (nc86ctl != null && isGIMIC) {
//            int n = nc86ctl.getNumberOfChip();
//            for (int i = 0; i < n; i++) {
//                NIRealChip rc = nc86ctl.getChipInterface(i);
//                if (rc != null) {
//                    int timeOutCounter;
//                    timeOutCounter = 10000;
//                    while ((rc. @in(0x0) &0x83) !=0 && timeOutCounter > 0) {
//                        Thread.Sleep(1);
//                        timeOutCounter--;
//                        //Debug.WriteLine("%x", rc.@in(0x0));
//                    }
//                    timeOutCounter = 10000;
//                    while ((rc. @in(0x100) &0xbf) !=0 && timeOutCounter > 0) {
//                        Thread.Sleep(1);
//                        timeOutCounter--;
//                    }
//                }
//            }
        }

        try { Thread.sleep(1000); } catch (InterruptedException ignore) {}
    }

    public RSoundChip SearchOPNA() {
        if (nScci != null) {
            int iCount = NScci.NSoundInterfaceManager().getInterfaceCount();
            for (int i = 0; i < iCount; i++) {
                NSoundInterface iIntfc = NScci.NSoundInterfaceManager().getInterface(i);
                NSCCI_INTERFACE_INFO iInfo = NScci.NSoundInterfaceManager().getInterfaceInfo(i);
                int sCount = iIntfc.getSoundChipCount();
                for (int s = 0; s < sCount; s++) {
                    NSoundChip sc = iIntfc.getSoundChip(s);
                    int t = sc.getSoundChipType();
                    if (t != 1) {
                        continue;
                    }

                    String ChipName = sc.getSoundChipInfo().cSoundChipName;
                    String InterfaceName = iInfo.cInterfaceName;
                    RScciSoundChip rsc = new RScciSoundChip(0, i, s);
                    rsc.scci = nScci;

                    return rsc;
                }
            }
        }

        if (nc86ctl != null) {
            int iCount = nc86ctl.getNumberOfChip();
            for (int i = 0; i < iCount; i++) {
                NIRealChip rc = nc86ctl.getChipInterface(i);
                NIGimic2 gm = rc.QueryInterface();
                ChipType cct = gm.getModuleType();

                if (cct != ChipType.CHIP_YM2608 && cct != ChipType.CHIP_YMF288) {
                    continue;
                }

                int o;
                String seri = gm.getModuleInfo().Serial;
                try {
                    o = Integer.parseInt(seri);
                } catch (NumberFormatException e) {
                    o = -1;
                }
                String ChipName = gm.getModuleInfo().Devname;
                String InterfaceName = gm.getMBInfo().Devname;

                RC86ctlSoundChip rsc = new RC86ctlSoundChip(-1, i, o);
                rsc.c86ctl = nc86ctl;
                return rsc;
            }
        }

        return null;

    }

    public static class RSoundChip {
        protected int SoundLocation;

        protected int BusID;

        protected int SoundChip;

        public int dClock = 3579545;

        public RSoundChip(int soundLocation, int busID, int soundChip) {
            SoundLocation = soundLocation;
            BusID = busID;
            SoundChip = soundChip;
        }

        public void init() {
            throw new UnsupportedOperationException();
        }

        public void setRegister(int adr, int dat) {
            throw new UnsupportedOperationException();
        }

        public int getRegister(int adr) {
            throw new UnsupportedOperationException();
        }

        public boolean isBufferEmpty() {
            throw new UnsupportedOperationException();
        }

        public int SetMasterClock(int mClock) {
            throw new UnsupportedOperationException();
        }

        public void setSSGVolume(int vol) {
            throw new UnsupportedOperationException();
        }
    }

    public static class RScciSoundChip extends RSoundChip {
        public NScci scci = null;

        private NSoundChip realChip = null;

        public RScciSoundChip(int soundLocation, int busID, int soundChip) {
            super(soundLocation, busID, soundChip);
        }

        @Override
        public void init() {
            NSoundInterface nsif = NScci.NSoundInterfaceManager().getInterface(BusID);
            NSoundChip nsc = nsif.getSoundChip(SoundChip);
            realChip = nsc;
            dClock = (int) nsc.getSoundChipClock();

            // If you want to send initialization commands for each chip type
            switch (RealChipType.values()[nsc.getSoundChipType()]) {
            case YM2608:
                // setRegister(0x2d, 00);
                // setRegister(0x29, 82);
                // setRegister(0x07, 38);
                break;
            }
        }

        @Override
        public void setRegister(int adr, int dat) {
            realChip.setRegister(adr, dat);
        }

        @Override
        public int getRegister(int adr) {
            return realChip.getRegister(adr);
        }

        @Override
        public boolean isBufferEmpty() {
            return realChip.isBufferEmpty();
        }

        /**
         * Master Clock Settings
         *
         * @param mClock The value you want to set
         * @return The actual value set
         */
        @Override
        public int SetMasterClock(int mClock) {
            // SCCI cannot change the clock

            return (int) realChip.getSoundChipClock();
        }

        @Override
        public void setSSGVolume(int vol) {
            // SCCI cannot change SSG volume
        }
    }

    public static class RC86ctlSoundChip extends RSoundChip {
        public Nc86ctl c86ctl = null;

        public Nc86ctl.NIRealChip realChip = null;

        public Nc86ctl.ChipType chiptype = ChipType.CHIP_UNKNOWN;

        public RC86ctlSoundChip(int soundLocation, int busID, int soundChip) {
            super(soundLocation, busID, soundChip);
        }

        @Override
        public void init() {
            NIRealChip rc = c86ctl.getChipInterface(BusID);
            rc.reset();
            realChip = rc;
            NIGimic2 gm = rc.QueryInterface();
            dClock = gm.getPLLClock();
            chiptype = gm.getModuleType();
            if (chiptype == ChipType.CHIP_YM2608) {
                // setRegister(0x2d, 00);
                // setRegister(0x29, 82);
                // setRegister(0x07, 38);
            }
        }

        @Override
        public void setRegister(int adr, int dat) {
            realChip.accept((short) adr, (byte) dat);
        }

        @Override
        public int getRegister(int adr) {
            return realChip.apply((short) adr);
        }

        @Override
        public boolean isBufferEmpty() {
            return true;
        }

        /**
         * Master Clock Settings
         *
         * @param mClock The value you want to set
         * @return The actual value set
         */
        @Override
        public int SetMasterClock(int mClock) {
            NIGimic2 gm = realChip.QueryInterface();
            int nowClock = gm.getPLLClock();
            if (nowClock != mClock) {
                gm.setPLLClock(mClock);
            }

            return gm.getPLLClock();
        }

        @Override
        public void setSSGVolume(int vol) {
            NIGimic2 gm = realChip.QueryInterface();
            gm.setSSGVolume(vol);
        }
    }
}
