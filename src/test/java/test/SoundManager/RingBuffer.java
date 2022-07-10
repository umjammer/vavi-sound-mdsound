
package test.SoundManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class RingBuffer {

    private List<Pack.PPack> buf;

    private Pack.PPack enqPos = null;
    private Pack.PPack deqPos = null;
    private Pack.PPack srcPos = null;
    private Pack.PPack tmpPos = null;

    private int bLength = 0;

    public boolean autoExtend = true;

    private final Object lockObj = new Object();

    public RingBuffer(int size) {
        if (size < 2)
            return;

        init(size);
    }

    public void init(int size) {
        buf = new ArrayList<>(Arrays.asList(new Pack.PPack()));
        for (int i = 1; i < size + 1; i++) {
            buf.add(new Pack.PPack());
            buf.get(i).prev = buf.get(i - 1);
            buf.get(i - 1).next = buf.get(i);
        }
        buf.get(0).prev = buf.get(buf.size() - 1);
        buf.get(buf.size() - 1).next = buf.get(0);

        enqPos = buf.get(0);
        deqPos = buf.get(0);
        bLength = 0;
    }

    public boolean enq(long counter, int dev, int typ, int adr, int val, Object[] ex) {
        synchronized (lockObj) {
            if (enqPos.next == deqPos) {
                if (!autoExtend) {
                    return false;
                }
                // 自動拡張
                try {
                    Pack.PPack p = new Pack.PPack();
                    buf.add(p);
                    p.prev = enqPos;
                    p.next = enqPos.next;
                    enqPos.next = p;
                    p.next.prev = p;
                } catch (Exception e) {
                    return false;
                }
            }

            bLength++;

            // データをセット
            enqPos.counter = counter;
            enqPos.pack.copy(dev, typ, adr, val, ex);

            if (counter >= enqPos.prev.counter) {
                enqPos = enqPos.next;

                // debugDispBuffer();

                return true;
            }

            Pack.PPack lastPos = enqPos.prev;
            // サーチ
            srcPos = enqPos.prev;
            while (counter < srcPos.counter && srcPos != deqPos) {
                srcPos = srcPos.prev;
            }

            if (counter < srcPos.counter && srcPos == deqPos) {
                srcPos = srcPos.prev;
                deqPos = enqPos;
            }

            // enqPosをリングから切り出す。
            Pack.PPack nextPack = enqPos;
            enqPos.prev.next = enqPos.next;
            enqPos.next.prev = enqPos.prev;

            // enqPosを挿入する
            tmpPos = srcPos.next;
            tmpPos.prev = enqPos;
            srcPos.next = enqPos;
            enqPos.prev = srcPos;
            enqPos.next = tmpPos;

            enqPos = lastPos.next;

            // debugDispBuffer();

            return true;
        }
    }

    public boolean deq(Long counter, int dev, int typ, int adr, int val, Object[] ex) {
        synchronized (lockObj) {
            counter = deqPos.counter;

            dev = deqPos.pack.dev;
            typ = deqPos.pack.typ;
            adr = deqPos.pack.adr;
            val = deqPos.pack.val;
            ex = deqPos.pack.ex;

            if (enqPos == deqPos)
                return false;

            bLength--;
            deqPos.counter = 0;
            deqPos = deqPos.next;

            // debugDispBuffer();

            return true;
        }
    }

    public int getDataSize() {
        synchronized (lockObj) {
            return bLength;
        }
    }

    public long lookUpCounter() {
        synchronized (lockObj) {
            return deqPos.counter;
        }
    }

//#if DEBUG

    public void debugDispBuffer() {
        synchronized (lockObj) {
            Pack.PPack edbg = deqPos;
            do {
                System.err.printf("[%s:%s]::", edbg.counter, edbg.pack.dev);
                edbg = edbg.next;
            } while (edbg != enqPos.next);
            System.err.println();
        }
    }

//#endif
}
