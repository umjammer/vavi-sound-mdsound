/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package real;

import com.sun.jna.Structure;
import real.nscci.NScci.NSoundChip;


/**
 * NSoundInterface.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-02-01 nsano initial version <br>
 */
public
class NSoundInterface extends Structure {

    public int getSoundChipCount() {
        return 0;
    }

    public NSoundChip getSoundChip(int j) {
        return null;
    }
}
