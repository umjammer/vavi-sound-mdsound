package test.soundManager;

public class Pack {

    public int dev;
    public int typ;
    public int adr;
    public int val;

    public Object[] ex;

    public Pack() {
    }

    public Pack(int dev, int typ, int adr, int val, Object... ex) {
        this.dev = dev;
        this.typ = typ;
        this.adr = adr;
        this.val = val;
        this.ex = ex;
    }

    public void copy(Pack pack) {
        dev = pack.dev;
        typ = pack.typ;
        adr = pack.adr;
        val = pack.val;
        ex = pack.ex;
    }

    public void copy(int dev, int typ, int adr, int val, Object... ex) {
        this.dev = dev;
        this.typ = typ;
        this.adr = adr;
        this.val = val;
        this.ex = ex;
    }

    static class PPack {
        public PPack prev;

        public PPack next;

        public int counter;

        public Pack pack = new Pack();
    }
}
