
package test.SoundManager;

import test.SoundManager.SoundManager.Snd;

public class ChipSender extends BaseSender {
    protected final Snd ActionOfChip;
    protected boolean busy = false;

    public ChipSender(Snd ActionOfChip, int BufferSize/* DATA_SEQUENCE_FREQUENCE */) {
        action = this::Main;
        ringBuffer = new RingBuffer(BufferSize) {
            {
                AutoExtend = false;
            }
        };
        this.ringBufferSize = BufferSize;
        this.ActionOfChip = ActionOfChip;
    }

    public boolean IsBusy() {
        synchronized (lockObj) {
            return busy;
        }
    }

    protected void Main() {
    }
}
