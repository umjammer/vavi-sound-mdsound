/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package real.nscci;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import real.NSoundInterface;


/**
 * NScci.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-01 nsano initial version <br>
 */
public interface NScci extends Library {

    NScci INSTANCE = Native.load("NScci", NScci.class);

    NativeLibrary NATIVE_LIBRARY = NativeLibrary.getInstance("NScci");

    void Dispose();

    class NSCCI_INTERFACE_INFO extends Structure {

        public String cInterfaceName;
    }

    class NSoundInterfaceManager extends Structure {
        NSoundInterfaceManager(Pointer p) {
            super(p);
        }

        public int getInterfaceCount() {
            return 0;
        }

        public void setLevelDisp(boolean b) {
        }

        public NSoundInterface getInterface(int i) {
            return null;
        }

        public void reset() {
        }

        public void sendData() {
        }

        public NSCCI_INTERFACE_INFO getInterfaceInfo(int i) {
            return null;
        }

        public boolean isBufferEmpty() {
            return false;
        }
    }

    class NSCCI_SOUND_CHIP_INFO extends Structure {

        public String cSoundChipName;
    }

    class NSoundChip extends Structure {

        public NSCCI_SOUND_CHIP_INFO getSoundChipInfo() {
            return null;
        }

        public int getSoundChipType() {
            return 0;
        }

        public Object getSoundChipClock() {
            return null;
        }

        public void setRegister(int adr, int dat) {
        }

        public int getRegister(int adr) {
            return 0;
        }

        public boolean isBufferEmpty() {
            return false;
        }

        public void init() {

        }
    }

    static NSoundInterfaceManager NSoundInterfaceManager() {
        return new NSoundInterfaceManager(NSoundInterfaceManager_);
    }

    Pointer NSoundInterfaceManager_ = NATIVE_LIBRARY
            .getGlobalVariableAddress("NSoundInterfaceManager_").getPointer(0);
}
