
package test.SoundManager;

public class EmuChipSender extends ChipSender {

    public EmuChipSender(int bufferSize/* = DATA_SEQUENCE_FREQUENCE */)

    {
        super(null, bufferSize);
    }

    public RingBuffer receiveBuffer = new RingBuffer(DATA_SEQUENCE_FREQUENCE);

    @Override
    protected void main() {
        try {
            while (true) {
                while (!getStart()) {
                    Thread.sleep(100);
                }

                synchronized (lockObj) {
                    isRunning = true;
                }

                while (true) {
                    Thread.yield();
                    if (ringBuffer.getDataSize() == 0) {
                        // 送信データが無く、停止指示がある場合のみ停止する
                        if (!getStart()) {
                            if (receiveBuffer.getDataSize() > 0) {
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
                        while (ringBuffer.deq(counter, dev, typ, adr, val, ex)) {
                            //actionOfChip.run(counter, dev, typ, adr, val, ex);
                            if (!receiveBuffer.enq(counter, dev, typ, adr, val, ex)) {
                                parent.setInterrupt();
                                while (!receiveBuffer.enq(counter, dev, typ, adr, val, ex)) {
                                }
                                parent.resetInterrupt();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    synchronized (lockObj) {
                        busy = false;
                    }
                }

                synchronized (lockObj) {
                    isRunning = false;
                    ringBuffer.init(ringBufferSize);
                    receiveBuffer.init(DATA_SEQUENCE_FREQUENCE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            synchronized (lockObj) {
                isRunning = false;
                start = false;
            }
        }
    }
}
