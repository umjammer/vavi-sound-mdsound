
package test.SoundManager;

import test.SoundManager.SoundManager.Snd;

public class ChipSender extends BaseSender {
    protected final Snd actionOfChip;
    protected boolean busy = false;

    public ChipSender(Snd actionOfChip, int bufferSize/* DATA_SEQUENCE_FREQUENCE */) {
        action = this::main;
        ringBuffer = new RingBuffer(bufferSize) {
            {
                autoExtend = false;
            }
        };
        this.ringBufferSize = bufferSize;
        this.actionOfChip = actionOfChip;
    }

    public boolean isBusy() {
        synchronized (lockObj) {
            return busy;
        }
    }

    protected void main() {
    }
}
