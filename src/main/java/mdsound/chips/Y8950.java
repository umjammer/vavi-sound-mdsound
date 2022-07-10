package mdsound.chips;


/**
 * Yamaha 3812 emulator interface - MAME VERSION
 *
 * @author Ernesto Corvi
 * @version
 * JB  28-04-2002  Fixed simultaneous usage of all three different chips types.
 *                 Used real sample rate when resample filter is active.<br/>
 * AAT 12-28-2001  Protected Y8950 from accessing unmapped port and keyboard handlers.<br/>
 * CHS 1999-01-09  Fixes new Ym3812 emulation interface.<br/>
 * CHS 1998-10-23  Mame streaming Sound chips update<br/>
 * EC  1998        Created Interface<br/>
 */
public class Y8950 {

    private byte CHIP_SAMPLING_MODE = 0;

    private Opl opl;

    private void setDeltatStatus(byte changeBits) {
        opl.setStatus(changeBits);
    }

    private void resetDeltatStatus(byte changeBits) {
        opl.resetStatus(changeBits);
    }

    public void start(int clock, int rate) {
        // emulator create
        this.opl = new Opl(clock, rate, Opl.TYPE_Y8950);
        opl.deltaT.statusSetHandler = this::setDeltatStatus;
        opl.deltaT.statusResetHandler = this::resetDeltatStatus;
    }

    public void stop() {
        opl.deltaT.memory = null;

        // emulator shutdown
        opl.destroy();
    }

    public void reset() {
        opl.reset();
    }

    public int write(int a, int v) {
        return opl.write(a, v);
    }

    public byte read(int a) {
        return opl.read(a);
    }

    private int timerOver(int c) {
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

    private void setDeltaTMemory(byte[] memory, int size) {
        opl.deltaT.memory = memory;
        opl.deltaT.memorySize = size;
    }

    public void writePcmRom(int romSize, int dataStart, int dataLength, byte[] romData) {
        opl.writePcmRom(romSize, dataStart, dataLength, romData);
    }

    public void writePcmRom(int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        opl.writePcmRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    /**
     * Generate samples for one of the Y8950's
     *
     * @param buffer is the output buffer pointer
     * @param length is the number of samples that should be generated
     */
    public void update(int[][] buffer, int length) {
        opl.updateOne(buffer, length);
    }

    public void setPortHandler(Opl.PortWriteHandler writePortHandler, Opl.PortReadHandler readPortHandler) {
        opl.portWriteHandler = writePortHandler;
        opl.portReadHandler = readPortHandler;
    }

    public void setKeyboardHandler(Opl.PortWriteHandler writeKeyboardHandler, Opl.PortReadHandler readKeyboardHandler) {
        opl.keyboardWriteHandler = writeKeyboardHandler;
        opl.keyboardReadHandler = readKeyboardHandler;
    }

    public void setMuteMask(int muteMask) {
        opl.setMuteMask(muteMask);
    }
}
