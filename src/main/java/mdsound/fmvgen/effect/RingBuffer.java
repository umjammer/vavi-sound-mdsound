package mdsound.fmvgen.effect;

public class RingBuffer {

    /** Read Position */
    private int rpos;
    /** Write position */
    private int wpos;
    /** Internal Buffer */
    private final float[] buf;
    private final int rbSize;

    /** Perform initialization */
    public RingBuffer(int clock, float RB/* = 4.0f*/) {
        rbSize = (int) (clock * RB);
        rpos = 0;
        wpos = (int) (rbSize / 2.0); // Set it to about half the buffer size for now.

        buf = new float[rbSize];
    }

    /**
     * A function that sets the interval between the read and write positions.
     * In the case of a delay effect, this becomes the delay time.
     */
    public void setInterval(int interval) {
        // Set the interval between the read and write positions

        // Process so that the value does not go below 0 or above the buffer size
        interval = interval % rbSize;
        if (interval <= 0) {
            interval = 1;
        }

        // Set the write position to be the interval away from the read position
        wpos = (rpos + interval) % rbSize;
    }

    /**
     * A function to read data from the internal buffer at read position {@link #rpos}
     * @param pos Relative to the read position {@link #rpos}
     * (The relative position (pos) is used for effects such as chorus and pitch shifter.)
     */
    public float read(int pos/* = 0*/) {
        // Calculate the actual read position from the read position (rpos) and relative position (pos).
        int tmp = rpos + pos;
        while (tmp < 0) {
            tmp += rbSize;
        }
        tmp %= rbSize; // Process so that the buffer size is not exceeded

        // Returns the value of the read position
        return buf[tmp];
    }

    /**
     * A function that writes data to the internal buffer at write position {@link #wpos}.
     */
    public void write(float in_) {
        // Write a value to the write position (wpos)
        buf[wpos] = in_;
    }

    /**
     * A function that advances the read position {@link #rpos}
     * and write position {@link #wpos} of the internal buffer by one.
     */
    public void update() {
        // Advances the internal buffer's read position (rpos) and write position (wpos) by one.
        rpos = (rpos + 1) % rbSize;
        wpos = (wpos + 1) % rbSize;
    }
}
