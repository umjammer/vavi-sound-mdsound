
package test.SoundManager;

import test.SoundManager.SoundManager.Snd;


public class RealChipSender extends ChipSender {

    public RealChipSender(Snd actionOfRealChip,
            int bufferSize /* = DATA_SEQUENCE_FREQUENCE */) {
        super(actionOfRealChip, bufferSize);
    }

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
                        if (!getStart())
                            break;
                        continue;
                    }

                    // dataが貯まってます！
                    synchronized (lockObj) {
                        busy = true;
                    }

                    try {
                        while (ringBuffer.deq(counter, dev, typ, adr, val, ex)) {
                            actionOfChip.accept(counter, dev, typ, adr, val, ex);
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
