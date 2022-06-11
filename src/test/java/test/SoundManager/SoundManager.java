
package test.SoundManager;

import test.Common.HexaConsumer;
import test.Common.HexaFunction;


public class SoundManager {
    public interface Enq extends HexaFunction<Long, Integer, Integer, Integer, Integer, Object[], Boolean> {
    }

    public interface Deq extends HexaFunction<Long, Integer, Integer, Integer, Integer, Object[], Boolean> {
    }

    public interface Snd extends HexaConsumer<Long, Integer, Integer, Integer, Integer, Object[]> {
    }

    public static final int DATA_SEQUENCE_FREQUENCE = 44100;

    /// <summary>
    /// ミュージックデータ解析
    /// 処理周期 : 無し
    /// データ受取時 : DataSenderへ即送信
    /// DataSenderが受け取ることができない状態の場合は、待ち合わせする
    /// </summary>
    private DataMaker dataMaker;

    /// <summary>
    /// データ送信
    /// 処理周期 : 44100Hz(Default)
    /// SeqCounter値に合わせて各ChipSenderへデータを振り分けながら送信。
    /// ChipSenderが受け取ることができない状態の場合は、待ち合わせする
    /// </summary>
    private DataSender dataSender;

    /// <summary>
    /// エミュチップ専門データ送信
    /// 処理周期 : 無し
    /// データが来たら、エミュレーションむけリングバッファにEnqueue
    /// Enqueueできない場合は、待ち合わせする
    /// </summary>
    private EmuChipSender emuChipSender;

    /// <summary>
    /// 実チップ専門データ送信
    /// 処理周期 : 無し
    /// データが来たら、実チップ向けコールバックを実施
    /// 待ち合わせ無し
    /// </summary>
    private RealChipSender realChipSender;

    /// <summary>
    /// 割り込み処理カウンタ
    /// 割り込みが発生している(1以上の)間、DataSenderは各チップへデータを送信しない
    /// </summary>
    private int interruptCounter = 0;

    private volatile Object lockObj = new Object();

    /// <summary>
    /// セットアップ
    /// </summary>
    /// <param name="DriverAction">ミュージックドライバーの1フレームあたりの処理を指定してください</param>
    /// <param name="RealChipAction">実チップ向けデータ送信処理を指定してください</param>
    /// <param name="startData">DataSenderが初期化を行うときに出力するデータを指定してください</param>
    /// <param name="stopData">DataSenderが演奏停止を行うときに出力するデータを指定してください</param>
    public void Setup(DriverAction DriverAction, Snd RealChipAction, Pack[] startData, Pack[] stopData) {
        dataMaker = new DataMaker(DriverAction);
        emuChipSender = new EmuChipSender(DATA_SEQUENCE_FREQUENCE);
        realChipSender = new RealChipSender(RealChipAction, DATA_SEQUENCE_FREQUENCE);
        dataSender = new DataSender(emuChipSender::Enq, realChipSender::Enq, startData, stopData);

        dataMaker.parent = this;
        emuChipSender.parent = this;
        realChipSender.parent = this;
        dataSender.parent = this;

        dataMaker.Mount();
        dataSender.Mount();
        emuChipSender.Mount();
        realChipSender.Mount();
    }

    public void release() {
        dataMaker.Unmount();
        dataSender.Unmount();
        emuChipSender.Unmount();
        realChipSender.Unmount();
    }

    public void RequestStart() {
        dataSender.Init();

        dataMaker.RequestStart();
        while (!dataMaker.IsRunning())
            ;
        dataSender.RequestStart();
        while (!dataSender.IsRunning())
            ;

        emuChipSender.RequestStart();
        realChipSender.RequestStart();
    }

    public void requestStop() {
        while (dataMaker.IsRunning())
            dataMaker.RequestStop();
        while (dataSender.IsRunning())
            dataSender.RequestStop();
        while (emuChipSender.IsRunning())
            emuChipSender.RequestStop();
        while (realChipSender.IsRunning())
            realChipSender.RequestStop();
    }

    public void RequestStopAtDataMaker() {
        dataMaker.RequestStop();
    }

    public void RequestStopAtEmuChipSender() {
        emuChipSender.RequestStop();
    }

    public void RequestStopAtRealChipSender() {
        realChipSender.RequestStop();
    }

    public boolean IsRunningAtDataMaker() {
        return dataMaker.IsRunning();
    }

    public boolean IsRunningAtDataSender() {
        return dataSender.IsRunning();
    }

    public boolean IsRunningAtRealChipSender() {
        return realChipSender.IsRunning();
    }

    public long GetDriverSeqCounterDelay() {
        return (long) (DATA_SEQUENCE_FREQUENCE * 0.1);
    }

    public boolean IsRunningAtEmuChipSender() {
        return emuChipSender.IsRunning();
    }

    /// <summary>
    /// DriverのデータをEnqueueするメソッドを取得する
    /// </summary>
    /// <returns></returns>
    public Enq GetDriverDataEnqueue() {
        return dataSender::Enq;
    }

    /// <summary>
    /// EmuのデータをDequeueするメソッドを取得する
    /// </summary>
    /// <returns></returns>
    public Deq GetEmuDataDequeue() {
        return emuChipSender::Deq;
    }

    /// <summary>
    /// RealのデータをDequeueするメソッドを取得する
    /// </summary>
    /// <returns></returns>
    public Deq GetRealDataDequeue() {
        return realChipSender::Deq;
    }

    public RingBuffer GetEmuRecvBuffer() {
        return emuChipSender.recvBuffer;
    }

    public boolean isRunningAsync() {
        if (dataMaker.IsRunning())
            return true;
        if (dataSender.IsRunning())
            return true;
        if (emuChipSender.IsRunning())
            return true;
        if (realChipSender.IsRunning())
            return true;

        return false;
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

    public boolean GetInterrupt() {
        synchronized (lockObj) {
            return (interruptCounter > 0);
        }
    }

    public long GetSeqCounter() {
        return dataSender.GetSeqCounter();
    }

    public long GetDataSenderBufferCounter() {
        return dataSender.GetRingBufferCounter();
    }

    public long GetDataSenderBufferSize() {
        return dataSender.GetRingBufferSize();
    }

    public long GetEmuChipSenderBufferSize() {
        return emuChipSender.GetRingBufferSize();
    }

    public long GetRealChipSenderBufferSize() {
        return realChipSender.GetRingBufferSize();
    }
}
