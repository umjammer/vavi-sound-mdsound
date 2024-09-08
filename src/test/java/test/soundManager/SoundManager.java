
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
     * ミュージックデータ解析
     * 処理周期 : 無し
     * データ受取時 : dataSenderへ即送信
     * dataSenderが受け取ることができない状態の場合は、待ち合わせする
     */
    private DataMaker dataMaker;

    /**
     * データ送信
     * 処理周期 : 44100Hz(Default)
     * SeqCounter値に合わせて各ChipSenderへデータを振り分けながら送信。
     * ChipSenderが受け取ることができない状態の場合は、待ち合わせする
     */
    private DataSender dataSender;

    /**
     * エミュチップ専門データ送信
     * 処理周期 : 無し
     * データが来たら、エミュレーションむけリングバッファにEnqueue
     * Enqueueできない場合は、待ち合わせする
     */
    private EmuChipSender emuChipSender;

    /**
     * 実チップ専門データ送信
     * 処理周期 : 無し
     * データが来たら、実チップ向けコールバックを実施
     * 待ち合わせ無し
     */
    private RealChipSender realChipSender;

    /**
     * 割り込み処理カウンタ
     * 割り込みが発生している(1以上の)間、DataSenderは各チップへデータを送信しない
     */
    private int interruptCounter = 0;

    private final Object lockObj = new Object();

    /**
     * セットアップ
     * @param driverAction ミュージックドライバーの1フレームあたりの処理を指定してください
     * @param realChipAction 実チップ向けデータ送信処理を指定してください
     * @param startData dataSenderが初期化を行うときに出力するデータを指定してください
     * @param stopData dataSenderが演奏停止を行うときに出力するデータを指定してください
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
     * DriverのデータをEnqueueするメソッドを取得する
     */
    public Enq getDriverDataEnqueue() {
        return dataSender::enq;
    }

    /**
     * EmuのデータをDequeueするメソッドを取得する
     */
    public Deq getEmuDataDequeue() {
        return emuChipSender::deq;
    }

    /**
     * RealのデータをDequeueするメソッドを取得する
     */
    public Deq getRealDataDequeue() {
        return realChipSender::deq;
    }

    public RingBuffer getEmuRecvBuffer() {
        return emuChipSender.receiveBuffer;
    }

    public boolean isRunningAsync() {
if (true) return false;
        else
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
