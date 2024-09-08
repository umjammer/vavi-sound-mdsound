package mdsound.chips;


/**
 * Yamaha 3812 emulator interface - MAME VERSION
 *
 * @author Ernesto Corvi
 * @version
 *   JB  28-04-2002  Fixed simultaneous usage of all three different chip types.
 *                   Used real sample rate when resample filter is active.
 *   AAT 12-28-2001  Protected Y8950 from accessing unmapped port and keyboard handlers.
 *   CHS 1999-01-09  Fixes new ym3812 emulation interface.
 *   CHS 1998-10-23  Mame streaming sound chip update
 *   EC  1998        Created Interface
 */
class Ym3812 {

    private static final int MAX_OPL_CHIPS = 2;

    private Opl opl;

    /**
     * Initialize YM3526 emulator(s).
     *
     * @param clock is the chips clock in Hz
     * @param rate  is sampling rate
     */
    public void start(int clock, int rate) {
        // emulator create
        opl = new Opl(clock, rate, Opl.TYPE_YM3812);
        reset();
    }

    public void stop() {
        // emulator shutdown
        opl.destroy();
    }

    public void reset() {
        opl.reset();
    }

    public int write(int a, int v) {
        return opl.write(a, v);
    }

    public int read(int a) {
        // ym3812 always returns bit2 and bit1 in HIGH state
        return opl.read(a) | 0x06;
    }

    public int timerOver(int c) {
        return opl.timerOver(c);
    }

    public void setTimerHandler(Opl.TimerHandler timerHandler) {
        opl.setTimerHandler(timerHandler);
    }

    public void setIrqHandler(Opl.IrqHandler irqHandler) {
        opl.setIRQHandler(irqHandler);
    }

    public void setUpdateHandler(Opl.UpdateHandler updateHandler) {
        opl.setUpdateHandler(updateHandler);
    }

    /**
     * Generate samples for one of the YM3812's
     *
     * @param buffer is the output buffer pointer
     * @param length is the number of samples that should be generated
     */
    public void update(int[][] buffer, int length) {
        opl.updateOne(buffer, length);
    }
}
