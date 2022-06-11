package test.SoundManager;

import test.SoundManager.SoundManager.Enq;


public class DataSender extends BaseSender {
    private static long sw = System.currentTimeMillis();
    private static final double swFreq = DATA_SEQUENCE_FREQUENCE;
    private final int Frq;
    private final long Def_SeqCounter = -500;
    private long SeqCounter = Def_SeqCounter;
    private final Enq EmuEnq;
    private final Enq RealEnq;
    private final Pack[] startData;
    private final Pack[] stopData;

    public DataSender(Enq EmuEnq,
                      Enq RealEnq,
                      Pack[] startData,
                      Pack[] stopData) {
        this(EmuEnq,
             RealEnq,
             startData,
             stopData,
             DATA_SEQUENCE_FREQUENCE,
             DATA_SEQUENCE_FREQUENCE);
    }

    public DataSender(Enq EmuEnq,
            Enq RealEnq,
            Pack[] startData,
            Pack[] stopData,
            int BufferSize,
            int Frq) {
        action = this::Main;
        this.Frq = Frq;
        ringBuffer = new RingBuffer(BufferSize);
        ringBuffer.AutoExtend = false;
        this.ringBufferSize = BufferSize;
        SeqCounter = Def_SeqCounter;
        this.EmuEnq = EmuEnq;
        this.RealEnq = RealEnq;
        this.startData = startData;
        this.stopData = stopData;
    }

    public void ResetSeqCounter() {
        synchronized (lockObj) {
            SeqCounter = Def_SeqCounter;
        }
    }

    public long GetSeqCounter() {
        synchronized (lockObj) {
            return SeqCounter;
        }
    }

    public void Init() {
        SeqCounter = Def_SeqCounter;
        ringBuffer.Init(ringBufferSize);

        // 開始時のデータの送信
        if (startData != null) {
            for (Pack dat : startData) {
                // 振り分けてEnqueue
                if (dat.dev >= 0)
                    while (!EmuEnq.apply(0l, dat.dev, dat.typ, dat.adr, dat.val, null))
                        Thread.yield();
                else
                    while (!RealEnq.apply(0l, dat.dev, dat.typ, dat.adr, dat.val, null))
                        Thread.yield();
            }
        }

    }

    private void Main() {
        try {

            while (true) {
                while (!GetStart()) {
                    Thread.sleep(100);
                }

                synchronized (lockObj) {
                    isRunning = true;
                }

                double o = (System.currentTimeMillis() - sw) / swFreq;
                sw = System.currentTimeMillis();
                double step = 1 / (double) Frq;
                SeqCounter = Def_SeqCounter;

                while (true) {
                    if (!GetStart())
                        break;
                    Thread.yield();

                    double el1 = (System.currentTimeMillis() - sw) / swFreq;
                    sw = System.currentTimeMillis();
                    if (el1 - o < step)
                        continue;
                    if (el1 - o >= step * Frq / 100.0)// 閾値10ms
                    {
                        do {
                            o += step;
                        } while (el1 - o >= step);
                    } else {
                        o += step;
                    }

                    // lock (lockObj)
                    {
                        // 待ち合わせ割り込み
                        if (parent.GetInterrupt()) {
                            // Thread.Sleep(0);
                            continue;
                        }

                        SeqCounter++;
                        if (SeqCounter < 0)
                            continue;

                        if (ringBuffer.GetDataSize() == 0) {
                            if (!parent.IsRunningAtDataMaker()) {
                                // RequestStop();
                                break;
                            }
                            continue;
                        }
                        if (SeqCounter < ringBuffer.lookUpCounter())
                            continue;
                        // continue;
                    }

                    // dataが貯まってます！
                    while (SeqCounter >= ringBuffer.lookUpCounter()) {
                        if (!ringBuffer.deq(Counter, Dev, Typ, Adr, Val, Ex)) {
                            break;
                        }

                        // 振り分けてEnqueue
                        if (Dev >= 0)
                            while (!EmuEnq.apply(Counter, Dev, Typ, Adr, Val, Ex))
                                Thread.yield();
                        else
                            while (!RealEnq.apply(Counter, Dev, Typ, Adr, Val, Ex))
                                Thread.yield();
                    }
                }

                // 停止時のデータの送信
                if (stopData != null) {
                    for (Pack dat : stopData) {
                        // 振り分けてEnqueue
                        if (dat.dev >= 0)
                            while (!EmuEnq.apply(Counter, dat.dev, dat.typ, dat.adr, dat.val, null))
                                Thread.yield();
                        else
                            while (!RealEnq.apply(Counter, dat.dev, dat.typ, dat.adr, dat.val, null))
                                Thread.yield();
                    }
                }

                synchronized (lockObj) {
                    isRunning = false;
                    Counter = 0;
                    Start = false;
                }
                parent.RequestStopAtEmuChipSender();
                parent.RequestStopAtRealChipSender();
            }
        } catch (Exception e) {
            synchronized (lockObj) {
                isRunning = false;
                Start = false;
            }
        }
    }
}
