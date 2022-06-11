
package test.SoundManager;

public class EmuChipSender extends ChipSender {
    public EmuChipSender(int BufferSize/* = DATA_SEQUENCE_FREQUENCE */)

    {
        super(null, BufferSize);
    }

    public RingBuffer recvBuffer = new RingBuffer(DATA_SEQUENCE_FREQUENCE);

    @Override
    protected void Main() {
        try {
            while (true) {
                while (!GetStart()) {
                    Thread.sleep(100);
                }

                synchronized (lockObj) {
                    isRunning = true;
                }

                while (true) {
                    Thread.yield();
                    if (ringBuffer.GetDataSize() == 0) {
                        // 送信データが無く、停止指示がある場合のみ停止する
                        if (!GetStart()) {
                            if (recvBuffer.GetDataSize() > 0) {
                                continue;
                            }
                            break;
                        }
                        continue;
                    }

                    // dataが貯まってます！
                    synchronized (lockObj) {
                        busy = true;
                    }

                    try {
                        while (ringBuffer.deq(Counter, Dev, Typ, Adr, Val, Ex)) {
                            // ActionOfChip?.Invoke(Counter, Dev, Typ, Adr, Val, Ex);
                            if (!recvBuffer.Enq(Counter, Dev, Typ, Adr, Val, Ex)) {
                                parent.setInterrupt();
                                while (!recvBuffer.Enq(Counter, Dev, Typ, Adr, Val, Ex)) {
                                }
                                parent.resetInterrupt();
                            }
                        }
                    } catch (Exception e) {

                    }

                    synchronized (lockObj) {
                        busy = false;
                    }
                }

                synchronized (lockObj) {
                    isRunning = false;
                    ringBuffer.Init(ringBufferSize);
                    recvBuffer.Init(DATA_SEQUENCE_FREQUENCE);
                }

            }
        } catch (Exception e) {
            synchronized (lockObj) {
                isRunning = false;
                Start = false;
            }
        }
    }
}
