
package test.SoundManager;

import java.util.Arrays;
import java.util.List;


public class RingBuffer {

    private List<PPack> buf;

    private PPack enqPos = null;
    private PPack deqPos = null;
    private PPack srcPos = null;
    private PPack tmpPos = null;

    private int bLength = 0;

    public boolean AutoExtend = true;

    private final Object lockObj = new Object();

    public RingBuffer(int size) {
        if (size < 2)
            return;

        Init(size);
    }

    public void Init(int size) {
        buf = Arrays.asList(new PPack());
        for (int i = 1; i < size + 1; i++) {
            buf.add(new PPack());
            buf.get(i).prev = buf.get(i - 1);
            buf.get(i - 1).next = buf.get(i);
        }
        buf.get(0).prev = buf.get(buf.size() - 1);
        buf.get(buf.size() - 1).next = buf.get(0);

        enqPos = buf.get(0);
        deqPos = buf.get(0);
        bLength = 0;
    }

    public boolean Enq(long Counter, int Dev, int Typ, int Adr, int Val, Object[] Ex) {
        synchronized (lockObj) {
            if (enqPos.next == deqPos) {
                if (!AutoExtend) {
                    return false;
                }
                // 自動拡張
                try {
                    PPack p = new PPack();
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
            enqPos.Counter = Counter;
            enqPos.pack.Copy(Dev, Typ, Adr, Val, Ex);

            if (Counter >= enqPos.prev.Counter) {
                enqPos = enqPos.next;

                // debugDispBuffer();

                return true;
            }

            PPack lastPos = enqPos.prev;
            // サーチ
            srcPos = enqPos.prev;
            while (Counter < srcPos.Counter && srcPos != deqPos) {
                srcPos = srcPos.prev;
            }

            if (Counter < srcPos.Counter && srcPos == deqPos) {
                srcPos = srcPos.prev;
                deqPos = enqPos;
            }

            // enqPosをリングから切り出す。
            PPack nextPack = enqPos;
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

    public boolean Deq(Long Counter, int Dev, int Typ, int Adr, int Val, Object[] Ex) {
        synchronized (lockObj) {
            Counter = deqPos.Counter;

            Dev = deqPos.pack.Dev;
            Typ = deqPos.pack.Typ;
            Adr = deqPos.pack.Adr;
            Val = deqPos.pack.Val;
            Ex = deqPos.pack.Ex;

            if (enqPos == deqPos)
                return false;

            bLength--;
            deqPos.Counter = 0;
            deqPos = deqPos.next;

            // debugDispBuffer();

            return true;
        }
    }

    public int GetDataSize() {
        synchronized (lockObj) {
            return bLength;
        }
    }

    public long LookUpCounter() {
        synchronized (lockObj) {
            return deqPos.Counter;
        }
    }

//#if DEBUG

    public void debugDispBuffer() {
        synchronized (lockObj) {
            PPack edbg = deqPos;
            do {
                System.err.printf("[%s:%s]::", edbg.Counter, edbg.pack.Dev);
                edbg = edbg.next;
            } while (edbg != enqPos.next);
            System.err.println();
        }
    }

    //#endif 
}
