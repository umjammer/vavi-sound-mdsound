/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package mdsound.x68sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * GlobalTest.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-04-05 nsano initial version <br>
 */
class GlobalTest {

    @Test
    void test1() throws Exception {
        System.err.println("44: " + Global.OPMLOWPASS_44.length);
        System.err.println("48: " + Global.OPMLOWPASS_48.length);
        assertEquals(441, Global.OPMLOWPASS_44.length);
        assertEquals(64, Global.OPMLOWPASS_44[0].length);
        assertEquals(-22, Global.OPMLOWPASS_44[1][3]);
        assertEquals(-29, Global.OPMLOWPASS_44[439][60]);
        assertEquals(96, Global.OPMLOWPASS_48.length);
        assertEquals(64, Global.OPMLOWPASS_48[0].length);
        assertEquals(-15, Global.OPMLOWPASS_48[3][3]);
        assertEquals(-17, Global.OPMLOWPASS_48[93][60]);
    }

}
