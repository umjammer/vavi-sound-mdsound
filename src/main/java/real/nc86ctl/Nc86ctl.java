/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package real.nc86ctl;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;


/**
 * Nc86ctl.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-01 nsano initial version <br>
 */
public interface Nc86ctl extends Library {

    Nc86ctl INSTANCE = Native.load("Nc86ctl", Nc86ctl.class);

    class ModuleInfo extends Structure {

        public String Serial;
        public String Devname;
    }

    class NIGimic2 extends Structure {

        public int getPLLClock() {
            return 0;
        }

        public ChipType getModuleType() {
            return null;
        }

        public void setPLLClock(int mClock) {
        }

        public void setSSGVolume(int vol) {
        }

        public ModuleInfo getModuleInfo() {
            return null;
        }

        public ModuleInfo getMBInfo() {
            return null;
        }
    }

    class ChipType extends Structure {

        public static final ChipType CHIP_UNKNOWN = null;
        public static final ChipType CHIP_YM2608 = null;
        public static final ChipType CHIP_YMF288 = null;
    }

    class NIRealChip extends Structure {

        public void reset() {

        }

        public NIGimic2 QueryInterface() {
            return null;
        }

        public void accept(short adr, byte dat) {
        }

        public int apply(short adr) {
            return 0;
        }
    }

    NIRealChip getChipInterface(int i);

    void initialize();

    int getNumberOfChip();

    void deinitialize();
}
