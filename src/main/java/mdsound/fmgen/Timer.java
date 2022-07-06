package mdsound.fmgen;

public class Timer {
    public void reset() {
        timerACount = 0;
        timerBCount = 0;
    }

    public boolean count(int us) {
        boolean event = false;

        if (timerACount != 0) {
            timerACount -= us << 16;
            if (timerACount <= 0) {
                event = true;
                timerA();

                while (timerACount <= 0)
                    timerACount += timerA;

                if ((regTc & 4) != 0)
                    setStatus(1);
            }
        }
        if (timerBCount != 0) {
            timerBCount -= us << 12;
            if (timerBCount <= 0) {
                event = true;
                while (timerBCount <= 0)
                    timerBCount += timerB;

                if ((regTc & 8) != 0)
                    setStatus(2);
            }
        }
        return event;
    }

    public int getNextEvent() {
        int ta = ((timerACount + 0xffff) >> 16) - 1;
        int tb = ((timerBCount + 0xfff) >> 12) - 1;
        return (Math.min(ta, tb)) + 1;
    }


    protected void setStatus(int bit) {
    }

    protected void resetStatus(int bit) {
    }

    protected void setTimerBase(int clock) {
        timerStep = (int) (1000000.0 * 65536 / clock);
    }

    protected void setTimerA(int addr, int data) {
        int tmp;
        regta[addr & 1] = (byte) (data);
        tmp = (regta[0] << 2) + (regta[1] & 3);
        timerA = (1024 - tmp) * timerStep;
        // Debug.printf("Timer A = %d   %d us\n", tmp, timera >> 16);
    }

    protected void setTimerB(int data) {
        timerB = (256 - data) * timerStep;
        // Debug.printf("Timer B = %d   %d us\n", data, timerb >> 12);
    }

    protected void setTimerControl(int data) {
        int tmp = regTc ^ data;
        regTc = (byte) data;

        if ((data & 0x10) != 0)
            resetStatus(1);
        if ((data & 0x20) != 0)
            resetStatus(2);

        if ((tmp & 0x01) != 0)
            timerACount = (data & 1) != 0 ? timerA : 0;
        if ((tmp & 0x02) != 0)
            timerBCount = (data & 2) != 0 ? timerB : 0;
    }

    protected byte status;
    protected byte regTc;

    private void timerA() {
    }

    private byte[] regta = new byte[2];

    private int timerA, timerACount;
    private int timerB, timerBCount;
    private int timerStep;
}
