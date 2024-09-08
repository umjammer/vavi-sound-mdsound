
package test.soundManager;

import java.util.concurrent.CompletableFuture;

import dotnet4j.threading.CancellationToken;
import dotnet4j.threading.CancellationTokenSource;
import dotnet4j.threading.ThreadingTools;
import vavi.util.Debug;


public abstract class BaseMakerSender {
    public static final int DATA_SEQUENCE_FREQUENCE = 44100;

    private CancellationTokenSource tokenSource;
    private CancellationToken cancellationToken;
    protected CompletableFuture<Void> task = null;
    protected RingBuffer ringBuffer = null;
    protected Runnable action = null;
    protected volatile boolean start = false;
    protected volatile boolean isRunning = false;
    protected final Object lockObj = new Object();
    public SoundManager parent = null;

    public long GetRingBufferCounter() {
        return ringBuffer.lookUpCounter();
    }

    public long GetRingBufferSize() {
        return ringBuffer.getDataSize();
    }

    public boolean mount() {
        tokenSource = new CancellationTokenSource();
        cancellationToken = tokenSource.getToken();

        task = ThreadingTools.withCancellation(CompletableFuture.runAsync(action), cancellationToken);

        return true;
    }

    public boolean unmount() {
        if (!task.isDone()) {
            tokenSource.cancel();
        }
        // task.Wait(1000, cancellationToken);
        return true;
    }

    public boolean enq(int counter, int dev, int typ, int adr, int val, Object[] ex) {
        return ringBuffer.enq(counter, dev, typ, adr, val, ex);
    }

    public boolean deq(/* ref */ int[] counter, /* ref */ int[] dev, /* ref */ int[] typ, /* ref */ int[] adr, /* ref */ int[] val, /* ref */ Object[][] ex) {
        return ringBuffer.deq(counter, dev, typ, adr, val, ex);
    }

    public void requestStart() {
        synchronized (lockObj) {
            start = true;
        }
    }

    public void requestStop() {
        synchronized (lockObj) {
            start = false;
        }
    }

    protected boolean getStart() {
        synchronized (lockObj) {
            return start;
        }
    }

    public boolean isRunning() {
        synchronized (lockObj) {
//Debug.println(getClass().getName() + ": " + isRunning);
            return isRunning;
        }
    }
}
