package mdsound;


public class VisWaveBuffer {

    private final short[][] buf;

    private int curPos;

    private final int size;

    public VisWaveBuffer() {
        this(2048);
    }

    public VisWaveBuffer(int size) {
        buf = new short[2][];
        for (int i = 0; i < 2; i++) {
            buf[i] = new short[size];
        }
        curPos = 0;
        this.size = size;
    }

    public void enq(short l, short r) {
        buf[0][curPos] = l;
        buf[1][curPos] = r;
        curPos++;
        curPos %= size;
    }

    public void copy(short[][] dest) {
        int pos = curPos;
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < size; j++)
                dest[i][j] = buf[i][(pos + j) % size];
    }
}
