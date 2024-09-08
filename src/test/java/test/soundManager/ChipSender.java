
package test.soundManager;

import test.soundManager.SoundManager.Snd;
import vavi.util.Debug;


public abstract class ChipSender extends BaseSender {
    protected final Snd actionOfChip;
    protected boolean busy = false;

    public ChipSender(Snd actionOfChip, int bufferSize /* DATA_SEQUENCE_FREQUENCE */) {
        action = this::main;
Debug.println("action: " + action);
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

    protected abstract void main();
}
