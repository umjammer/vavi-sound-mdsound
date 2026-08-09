/*
 * NSFPlay/NFSPlug project by Brezza.
 *
 * https://web.archive.org/web/20160301201825/http://www.pokipoki.org/dsa/
 */

package mdsound.np.chip;


public class DeviceInfo {

    @Override
    public DeviceInfo clone() {
        return null;
    }

    public static class TrackInfo extends DeviceInfo {

        @Override
        public DeviceInfo clone() {
            return null;
        }

        /** Returns the current output value as is */
        public int getOutput() {
            return 0;
        }

        /** Returns the frequency in Hz */
        public double getFreqHz() {
            return 0;
        }

        /** Returns the frequency as a device dependent value. */
        public int getFreq() {
            return 0;
        }

        /** Return the volume */
        public int getVolume() {
            return 0;
        }

        /** Returns the maximum volume */
        public int getMaxVolume() {
            return 0;
        }

        /** True if sound is being played, false if it is OFF */
        public boolean getKeyStatus() {
            return false;
        }

        /** Tone Number */
        public int getTone() {
            return 0;
        }

        /** Converts frequency to note number. 0x60 is o4c, 0 is invalid. */
        public int getNote(double freq) {
            final double LOG2_440 = 8.7813597135246596040696824762152; // ln(440) / ln(2)
            final double LOG_2 = 0.69314718055994530941723212145818; // ln(2)
            final int NOTE_440HZ = 0x69;

            if (freq > 1.0)
                return (int) ((12 * (Math.log(freq) / LOG_2 - LOG2_440) + NOTE_440HZ + 0.5));
            else
                return 0;
        }
    }

    /** Buffering TrackInfo */
    static class InfoBuffer {
        static class Pair {
            int first;
            DeviceInfo second;
            Pair(int first, DeviceInfo second) {
                this.first = first;
                this.second = second;
            }
        }

        final int bufMax;

        int index;

        final Pair[] buffer;

        public InfoBuffer(int max /* = 60 * 10 */) {
            index = 0;
            bufMax = max;
            buffer = new Pair[bufMax];
            for (int i = 0; i < bufMax; i++) {
                buffer[i] = new Pair(0, null);
            }
        }

        public void clear() {
            for (int i = 0; i < bufMax; i++) {
                buffer[i].first = 0;
                buffer[i].second = null;
            }
        }

        public void addInfo(int pos, DeviceInfo di) {
            if (di != null) {
                buffer[index].first = pos;
                buffer[index].second = di.clone();
                index = (index + 1) % bufMax;
            }
        }

        public DeviceInfo getInfo(int pos) {
            if (pos == -1)
                return buffer[(index + bufMax - 1) % bufMax].second;

            for (int i = (index + bufMax - 1) % bufMax; i != index; i = (i + bufMax - 1) % bufMax)
                if (buffer[i].first <= pos)
                    return buffer[i].second;

            return null;
        }
    }

    public static class BasicTrackInfo extends TrackInfo implements Cloneable {

        public int output;
        public int volume;
        public int maxVolume;
        public int _freq;
        public double freq;
        public int freqP;
        public boolean halt;
        public boolean key;
        public int tone;
        public int freqShift;

        @Override
        public DeviceInfo clone() {
            BasicTrackInfo tib = new BasicTrackInfo();
            tib.output = output;
            tib.volume = volume;
            tib.maxVolume = maxVolume;
            tib._freq = _freq;
            tib.freq = freq;
            tib.key = key;
            tib.tone = tone;
            return tib;
        }

        @Override
        public int getOutput() {
            return output;
        }

        @Override
        public double getFreqHz() {
            return freq;
        }

        @Override
        public int getFreq() {
            return _freq;
        }

        @Override
        public boolean getKeyStatus() {
            return key;
        }

        @Override
        public int getVolume() {
            return volume;
        }

        @Override
        public int getMaxVolume() {
            return maxVolume;
        }

        @Override
        public int getTone() {
            return tone;
        }

        public boolean getHalt() {
            return halt;
        }

        public int getFreqP() {
            return freqP;
        }

        public int getFreqShift() {
            return freqShift;
        }
    }
}
