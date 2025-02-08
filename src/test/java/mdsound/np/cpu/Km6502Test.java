package mdsound.np.cpu;

import org.junit.jupiter.api.Test;
import vavi.util.Debug;

import static org.junit.jupiter.api.Assertions.*;


class Km6502Test {

    @Test
    void test1() throws Exception {
        Km6502 cpu = new Km6502(false);
        int r = cpu.KM_DEC(100);
Debug.println(r);
        assertEquals(99, r);
    }
}