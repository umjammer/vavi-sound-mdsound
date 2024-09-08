
package test.soundManager;

import vavi.util.Debug;


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
Debug.println("wait");
                while (!getStart()) {
                    Thread.sleep(100);
                }

                synchronized (lockObj) {
                    isRunning = true;
                }
Debug.println("start");

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
                        int[] counter_ = new int[1];
                        int[] dev_ = new int[1];
                        int[] typ_ = new int[1];
                        int[] adr_ = new int[1];
                        int[] val_ = new int[1];
                        Object[][] ex_ = new Object[1][];
                        while (ringBuffer.deq(counter_, dev_, typ_, adr_, val_, ex_)) {
                            counter = counter_[0];
                            dev = dev_[0];
                            typ = typ_[0];
                            adr = adr_[0];
                            val = val_[0];
                            ex = ex_[0];
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
Debug.println("stop");
                    isRunning = false;
                    ringBuffer.init(ringBufferSize);
                    receiveBuffer.init(DATA_SEQUENCE_FREQUENCE);
                }
            }
        } catch (Exception e) {
Debug.println(e);
            synchronized (lockObj) {
                isRunning = false;
                start = false;
            }
        }
    }
}
