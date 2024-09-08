
package test.soundManager;

import test.soundManager.SoundManager.Snd;


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
