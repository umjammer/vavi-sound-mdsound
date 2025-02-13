
package test.soundManager;

import dotnet4j.util.compat.HexaConsumer;
import dotnet4j.util.compat.HexaFunction;
import vavi.util.Debug;


public class SoundManager {

    public interface Enq extends HexaFunction<Integer, Integer, Integer, Integer, Integer, Object[], Boolean> {
    }

    public interface Deq extends HexaFunction<int[], int[], int[], int[], int[], Object[][], Boolean> {
    }

    public interface Snd extends HexaConsumer<Integer, Integer, Integer, Integer, Integer, Object[]> {
    }

    public static final int DATA_SEQUENCE_FREQUENCE = 44100;

    /**
     * Music Data Analysis
     * Processing cycle: None
     * When receiving data: Immediately send to dataSender
     * If the dataSender is not able to receive the data, wait for a while.
     */
    private DataMaker dataMaker;

    /**
     * Data transmission
     * Processing cycle: 44100Hz (Default)
     * Data is distributed and sent to each ChipSender according to the SeqCounter value.
     * If ChipSender is not available to receive, wait for a while.
     */
    private DataSender dataSender;

    /**
     * EmuChip specialized data transmission
     * Processing cycle: None
     * When data arrives, Enqueue it into the ring buffer for emulation.
     * If you can't enqueue, wait
     */
    private EmuChipSender emuChipSender;

    /**
     * Real chip specialized data transmission
     * Processing cycle: None
     * When data arrives, a callback is made to the actual chip.
     * No appointment
     */
    private RealChipSender realChipSender;

    /**
     * Interrupt Processing Counter
     * While an interrupt is occurring (1 or more), the DataSender does not send data to each chip.
     */
    private int interruptCounter = 0;

    private final Object lockObj = new Object();

    /**
     * set up
     * @param driverAction Please specify the processing per frame of the music driver
     * @param realChipAction Please specify the data transmission process for the actual chip.
     * @param startData Specify the data to be output when dataSender is initialized.
     * @param stopData Specify the data to be output when the dataSender stops playing.
     */
    public void setup(DriverAction driverAction, Snd realChipAction, Pack[] startData, Pack[] stopData) {
        dataMaker = new DataMaker(driverAction);
        emuChipSender = new EmuChipSender(DATA_SEQUENCE_FREQUENCE);
        realChipSender = new RealChipSender(realChipAction, DATA_SEQUENCE_FREQUENCE);
        dataSender = new DataSender(emuChipSender::enq, realChipSender::enq, startData, stopData);

        dataMaker.parent = this;
        emuChipSender.parent = this;
        realChipSender.parent = this;
        dataSender.parent = this;

        dataMaker.mount();
        dataSender.mount();
        emuChipSender.mount();
        realChipSender.mount();
    }

    public void release() {
        dataMaker.unmount();
        dataSender.unmount();
        emuChipSender.unmount();
        realChipSender.unmount();
    }

    public void requestStart() {
        dataSender.init();

        dataMaker.requestStart();
        while (!dataMaker.isRunning())
            Thread.yield();
        dataSender.requestStart();
        while (!dataSender.isRunning())
            Thread.yield();

        emuChipSender.requestStart();
        realChipSender.requestStart();
    }

    public void requestStop() {
        while (dataMaker.isRunning())
            dataMaker.requestStop();
        while (dataSender.isRunning())
            dataSender.requestStop();
        while (emuChipSender.isRunning())
            emuChipSender.requestStop();
        while (realChipSender.isRunning())
            realChipSender.requestStop();
    }

    public void requestStopAtDataMaker() {
        dataMaker.requestStop();
    }

    public void requestStopAtEmuChipSender() {
        emuChipSender.requestStop();
    }

    public void requestStopAtRealChipSender() {
        realChipSender.requestStop();
    }

    public boolean isRunningAtDataMaker() {
        return dataMaker.isRunning();
    }

    public boolean isRunningAtDataSender() {
        return dataSender.isRunning();
    }

    public boolean isRunningAtRealChipSender() {
        return realChipSender.isRunning();
    }

    public int getDriverSeqCounterDelay() {
        return (int) (DATA_SEQUENCE_FREQUENCE * 0.1);
    }

    public boolean isRunningAtEmuChipSender() {
        return emuChipSender.isRunning();
    }

    /**
     * Gets the method to enqueue the driver data.
     */
    public Enq getDriverDataEnqueue() {
        return dataSender::enq;
    }

    /**
     * Get the method to dequeue Emu data
     */
    public Deq getEmuDataDequeue() {
        return emuChipSender::deq;
    }

    /**
     * Get a method to dequeue Real data
     */
    public Deq getRealDataDequeue() {
        return realChipSender::deq;
    }

    public RingBuffer getEmuRecvBuffer() {
        return emuChipSender.receiveBuffer;
    }

    public boolean isRunningAsync() {
//if (true) return false;
//        else
try {
        if (dataMaker.isRunning())
            return true;
        if (dataSender.isRunning())
            return true;
        if (emuChipSender.isRunning())
            return true;
        if (realChipSender.isRunning())
            return true;

        return false;
} finally {
 Debug.printf("dm: %s, ds: %s, ecs: %s, rcs: %s", dataMaker.isRunning(), dataSender.isRunning(), emuChipSender.isRunning(), realChipSender.isRunning());
}
    }

    public void setInterrupt() {
        synchronized (lockObj) {
            interruptCounter++;
        }
    }

    public void resetInterrupt() {
        synchronized (lockObj) {
            if (interruptCounter > 0)
                interruptCounter--;
        }
    }

    public boolean getInterrupt() {
        synchronized (lockObj) {
            return (interruptCounter > 0);
        }
    }

    public int getSeqCounter() {
        return dataSender.getSeqCounter();
    }

    public long getDataSenderBufferCounter() {
        return dataSender.GetRingBufferCounter();
    }

    public long getDataSenderBufferSize() {
        return dataSender.GetRingBufferSize();
    }

    public long getEmuChipSenderBufferSize() {
        return emuChipSender.GetRingBufferSize();
    }

    public long getRealChipSenderBufferSize() {
        return realChipSender.GetRingBufferSize();
    }
}
