package test.SoundManager;

import test.SoundManager.SoundManager.Enq;


public class DataSender extends BaseSender {
    private static long sw = System.currentTimeMillis();
    private static final double swFreq = DATA_SEQUENCE_FREQUENCE;
    private final int frq;
    private static final long Def_SeqCounter = -500;
    private long seqCounter;
    private final Enq emuEnq;
    private final Enq realEnq;
    private final Pack[] startData;
    private final Pack[] stopData;

    public DataSender(Enq emuEnq,
                      Enq realEnq,
                      Pack[] startData,
                      Pack[] stopData) {
        this(emuEnq,
             realEnq,
             startData,
             stopData,
             DATA_SEQUENCE_FREQUENCE,
             DATA_SEQUENCE_FREQUENCE);
    }

    public DataSender(Enq emuEnq,
            Enq realEnq,
            Pack[] startData,
            Pack[] stopData,
            int bufferSize,
            int frq) {
        action = this::Main;
        this.frq = frq;
        ringBuffer = new RingBuffer(bufferSize);
        ringBuffer.autoExtend = false;
        this.ringBufferSize = bufferSize;
        seqCounter = Def_SeqCounter;
        this.emuEnq = emuEnq;
        this.realEnq = realEnq;
        this.startData = startData;
        this.stopData = stopData;
    }

    public void resetSeqCounter() {
        synchronized (lockObj) {
            seqCounter = Def_SeqCounter;
        }
    }

    public long getSeqCounter() {
        synchronized (lockObj) {
            return seqCounter;
        }
    }

    public void init() {
        seqCounter = Def_SeqCounter;
        ringBuffer.init(ringBufferSize);

        // 開始時のデータの送信
        if (startData != null) {
            for (Pack dat : startData) {
                // 振り分けてEnqueue
                if (dat.dev >= 0)
                    while (!emuEnq.apply(0l, dat.dev, dat.typ, dat.adr, dat.val, null))
                        Thread.yield();
                else
                    while (!realEnq.apply(0l, dat.dev, dat.typ, dat.adr, dat.val, null))
                        Thread.yield();
            }
        }
    }

    private void Main() {
        try {

            while (true) {
                while (!getStart()) {
                    Thread.sleep(100);
                }

                synchronized (lockObj) {
                    isRunning = true;
                }

                double o = (System.currentTimeMillis() - sw) / swFreq;
                sw = System.currentTimeMillis();
                double step = 1 / (double) frq;
                seqCounter = Def_SeqCounter;

                while (true) {
                    if (!getStart())
                        break;
                    Thread.yield();

                    double el1 = (System.currentTimeMillis() - sw) / swFreq;
                    sw = System.currentTimeMillis();
                    if (el1 - o < step)
                        continue;
                    if (el1 - o >= step * frq / 100.0) { // 閾値10ms
                        do {
                            o += step;
                        } while (el1 - o >= step);
                    } else {
                        o += step;
                    }

                    // lock (lockObj)
                    {
                        // 待ち合わせ割り込み
                        if (parent.getInterrupt()) {
                            // Thread.Sleep(0);
                            continue;
                        }

                        seqCounter++;
                        if (seqCounter < 0)
                            continue;

                        if (ringBuffer.getDataSize() == 0) {
                            if (!parent.isRunningAtDataMaker()) {
                                // RequestStop();
                                break;
                            }
                            continue;
                        }
                        if (seqCounter < ringBuffer.lookUpCounter())
                            continue;
                        // continue;
                    }

                    // dataが貯まってます！
                    while (seqCounter >= ringBuffer.lookUpCounter()) {
                        if (!ringBuffer.deq(counter, dev, typ, adr, val, ex)) {
                            break;
                        }

                        // 振り分けてEnqueue
                        if (dev >= 0)
                            while (!emuEnq.apply(counter, dev, typ, adr, val, ex))
                                Thread.yield();
                        else
                            while (!realEnq.apply(counter, dev, typ, adr, val, ex))
                                Thread.yield();
                    }
                }

                // 停止時のデータの送信
                if (stopData != null) {
                    for (Pack dat : stopData) {
                        // 振り分けてEnqueue
                        if (dat.dev >= 0)
                            while (!emuEnq.apply(counter, dat.dev, dat.typ, dat.adr, dat.val, null))
                                Thread.yield();
                        else
                            while (!realEnq.apply(counter, dat.dev, dat.typ, dat.adr, dat.val, null))
                                Thread.yield();
                    }
                }

                synchronized (lockObj) {
                    isRunning = false;
                    counter = 0;
                    start = false;
                }
                parent.requestStopAtEmuChipSender();
                parent.requestStopAtRealChipSender();
            }
        } catch (Exception e) {
            synchronized (lockObj) {
                isRunning = false;
                start = false;
            }
        }
    }
}
