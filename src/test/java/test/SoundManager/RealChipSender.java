
package test.SoundManager;

import test.SoundManager.SoundManager.Snd;


public class RealChipSender extends ChipSender {

    public RealChipSender(Snd ActionOfRealChip,
            int BufferSize /* = DATA_SEQUENCE_FREQUENCE */) {
        super(ActionOfRealChip, BufferSize);
    }

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
                        if (!GetStart())
                            break;
                        continue;
                    }

                    // dataが貯まってます！
                    synchronized (lockObj) {
                        busy = true;
                    }

                    try {
                        while (ringBuffer.deq(Counter, Dev, Typ, Adr, Val, Ex)) {
                            ActionOfChip.accept(Counter, Dev, Typ, Adr, Val, Ex);
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
