/*
 * NSFPlay/NFSPlug project by Brezza.
 *
 * https://web.archive.org/web/20160301201825/http://www.pokipoki.org/dsa/
 */

package mdsound.np;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


public interface Device {

    void reset();

    boolean write(int adr, int val, int id);

    default boolean write(int adr, int val) {
        return write(adr, val, 0);
    }

    boolean read(int adr, int[] val, int id);

    default boolean read(int adr, int[] val) {
        return read(adr, val, 0);
    }

    void setOption(int id, int val);

    interface Renderable extends Device {

        /**
         * Audio Rendering
         *
         * @param b The array that stores the composite data.
         *             b[0] is the left channel audio data and b[1] is the right channel audio data.
         * @return The size of the synthesized data. 1 is mono, 2 is stereo, 0 is synthesis failure.
         */
        int render(int[] b);

        /**
         * chips update/operation is now bound to CPU clocks
         * Render() now simply mixes and outputs Sound
         */
        void tick(int clocks);
    }

    /**
     * Audio synthesis chip
     */
    interface SoundChip extends Renderable {

        /**
         * Sound chip clocked by M2 (NTSC = ~1.789MHz)
         */
        @Override void tick(int clocks);

        /**
         * Set the chip's operating clock
         *
         * @param clock operating clock
         */
        void setClock(double clock);

        /**
         * Audio synthesis rate settings
         *
         * @param rate Output Frequency
         */
        void setRate(double rate);

        /**
         * Channel mask.
         */
        void setMask(int mask);

        /**
         * Stereo mix.
         * mixL = 0-256
         * mixR = 0-256
         * 128 = neutral
         * 256 = double
         * 0 = nil
         * <0 = inverted
         */
        void setStereoMix(int trk, int mixL, int mixR);

        /**
         * Track info for keyboard view.
         */
        //TrackInfo getTrackInfo(int trk) { return null; }

        void setListener(Consumer<int[]> listener);
    }

    class Bus implements Device {

        protected List<Device> vd = new ArrayList<>();

        /**
         * Reset
         * <p>
         * Calls the Reset method on all attached devices.
         * The order of calls is equal to the order in which the devices were installed.
         */
        @Override
        public void reset() {
            for (Device it : vd)
                it.reset();
        }

        /**
         * Detaches all devices.
         */
        public void detachAll() {
            vd.clear();
        }

        /**
         * Attaches the Device.
         * <p>
         * Attach the device to this bus.
         *
         * @param d A device to attach to.
         */
        public void attach(Device d) {
            vd.add(d);
        }

        /**
         * Writes.
         * <p>
         * Calls the Write method of all attached devices.
         * The order of calls is equal to the order in which the devices were installed.
         */
        @Override
        public boolean write(int adr, int val, int id/* = 0*/) {
            boolean ret = false;
            for (Device it : vd)
                ret |= it.write(adr, val);
            return ret;
        }

        /**
         * Reads.
         * <p>
         * Calls the Read method on all attached devices.
         * The order of calls is equal to the order in which the devices were installed.
         * The return value is the logical OR of the return values of valid devices (the Read method returns true).
         */
        @Override
        public boolean read(int adr, int[] val, int id/* = 0*/) {
            boolean ret = false;
            int[] vtmp = new int[] { 0 };

            val[0] = 0;
            for (Device it : vd) {
                if (it.read(adr, vtmp)) {
                    val[0] |= vtmp[0];
                    ret = true;
                }
            }
            return ret;
        }

        @Override
        public void setOption(int id, int val) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Layer.
     * <p>
     * It is similar to a bus, but does not propagate read and write operations to all devices.
     * The program will stop when it finds the first device that it can successfully read from and write to.
     */
    class Layer extends Bus {

        /**
         * Writes.
         * <p>
         * Call the Write method of the attached device.
         * The order of calls is equal to the order in which the devices were installed.
         * The process ends when a device that was successfully written to is found.
         */
        @Override
        public boolean write(int adr, int val, int id/* = 0*/) {
            for (Device it : vd) {
                if (it.write(adr, val)) return true;
            }

            return false;
        }

        /**
         * Reads.
         * <p>
         * Call the Read method of the attached device.
         * The order of calls is equal to the order in which the devices were installed.
         * The process ends when a device that was successfully read from is found.
         */
        @Override
        public boolean read(int adr, int[] val, int id/* = 0*/) {
            val[0] = 0;
            for (Device it : vd) {
                if (it.read(adr, val)) return true;
            }

            return false;
        }
    }
}

class Counter {
    // Note: For increased speed, I'll inline all of NSFPlay's Counter member functions.
    private static final int COUNTER_SHIFT = 24;

    public double ratio;
    public int val, step;

    void setCycle(int s) {
        this.step = (int) (this.ratio / (s + 1));
    }

    void iup() {
        this.val += this.step;
    }

    int value() {
        return this.val >> COUNTER_SHIFT;
    }

    void init(double clk, double rate) {
        this.ratio = (1 << COUNTER_SHIFT) * (1.0 * clk / rate);
        this.step = (int) (this.ratio + 0.5);
        this.val = 0;
    }
}
