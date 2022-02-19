
package test.SoundManager;

import java.util.concurrent.CompletableFuture;

import dotnet4j.threading.CancellationToken;
import dotnet4j.threading.CancellationTokenSource;
import dotnet4j.threading.ThreadingTools;

public abstract class BaseMakerSender {
    public static final int DATA_SEQUENCE_FREQUENCE = 44100;

    private CancellationTokenSource tokenSource;
    private CancellationToken cancellationToken;
    protected CompletableFuture<Void> task = null;
    protected RingBuffer ringBuffer = null;
    protected Runnable action = null;
    protected volatile boolean Start = false;
    protected volatile boolean isRunning = false;
    protected Object lockObj = new Object();
    public SoundManager parent = null;

    public long GetRingBufferCounter() {
        return ringBuffer.LookUpCounter();
    }

    public long GetRingBufferSize() {
        return ringBuffer.GetDataSize();
    }

    public boolean Mount() {
        tokenSource = new CancellationTokenSource();
        cancellationToken = tokenSource.getToken();

        task = ThreadingTools.withCancellation(CompletableFuture.runAsync(action), cancellationToken);

        return true;
    }

    public boolean Unmount() {
        if (!task.isDone()) {
            tokenSource.cancel();
        }
        // task.Wait(1000, cancellationToken);
        return true;
    }

    public boolean Enq(long Counter, int Dev, int Typ, int Adr, int Val, Object[] Ex) {
        return ringBuffer.Enq(Counter, Dev, Typ, Adr, Val, Ex);
    }

    public boolean Deq(long Counter, int Dev, int Typ, int Adr, int Val, Object[] Ex) {
        return ringBuffer.Deq(Counter, Dev, Typ, Adr, Val, Ex);
    }

    public void RequestStart() {
        synchronized (lockObj) {
            Start = true;
        }
    }

    public void RequestStop() {
        synchronized (lockObj) {
            Start = false;
        }
    }

    protected boolean GetStart() {
        synchronized (lockObj) {
            return Start;
        }
    }

    public boolean IsRunning() {
        synchronized (lockObj) {
            return isRunning;
        }
    }
}
