/*
 * https://web.archive.org/web/20200810215827/http://vstcpp.wpblog.jp/?p=1505
 */

package mdsound.fmvgen.effect;


/**
 * RingBuffer.
 *
 * @author twitter:@vstcpp
 * @see "https://web.archive.org/web/20200810215827/http://vstcpp.wpblog.jp/?p=1505"
 */
public class RingBuffer {

    /** Read Position */
    private int rPos;
    /** Write position */
    private int wPos;
    /** Internal Buffer */
    private final float[] buf;
    private final int rbSize;

    /** Perform initialization */
    public RingBuffer(int clock, float RB /* = 4.0f */) {
        rbSize = (int) (clock * RB);
        rPos = 0;
        wPos = (int) (rbSize / 2.0); // Set it to about half the buffer size for now.

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
        wPos = (rPos + interval) % rbSize;
    }

    /**
     * A function to read data from the internal buffer at read position {@link #rPos}
     * @param pos Relative to the read position {@link #rPos}
     * (The relative position (pos) is used for effects such as chorus and pitch shifter.)
     */
    public float read(int pos/* = 0*/) {
        // Calculate the actual read position from the read position (rPos) and relative position (pos).
        int tmp = rPos + pos;
        while (tmp < 0) {
            tmp += rbSize;
        }
        tmp %= rbSize; // Process so that the buffer size is not exceeded

        // Returns the value of the read position
        return buf[tmp];
    }

    /**
     * A function that writes data to the internal buffer at write position {@link #wPos}.
     */
    public void write(float in_) {
        // Write a value to the write position (wPos)
        buf[wPos] = in_;
    }

    /**
     * A function that advances the read position {@link #rPos}
     * and write position {@link #wPos} of the internal buffer by one.
     */
    public void update() {
        // Advances the internal buffer's read position (rPos) and write position (wPos) by one.
        rPos = (rPos + 1) % rbSize;
        wPos = (wPos + 1) % rbSize;
    }
}
