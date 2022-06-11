
package mdsound.np.chip;

public class DeviceInfo {
    public DeviceInfo clone() {
        return null;
    }

    public static class TrackInfo extends DeviceInfo {
        public DeviceInfo clone() {
            return null;
        }

        // 現在の出力値をそのまま返す
        public int getOutput() {
            return 0;
        }

        // 周波数をHzで返す
        public double getFreqHz() {
            return 0;
        }

        // 周波数をデバイス依存値で返す．
        public int getFreq() {
            return 0;
        }

        // 音量を返す
        public int getVolume() {
            return 0;
        }

        // 音量の最大値を返す
        public int getMaxVolume() {
            return 0;
        }

        // 発音中ならtrue OFFならfalse
        public boolean getKeyStatus() {
            return false;
        }

        // トーン番号
        public int getTone() {
            return 0;
        }

        // 周波数をノート番号に変換．0x60がo4c 0は無効
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

    /* TrackInfo を バッファリング */
    public static class InfoBuffer {
        public static class Pair {
            public int first;
            public DeviceInfo second;
            Pair(int first, DeviceInfo second) {
                this.first = first;
                this.second = second;
            }
        }

        public int bufmax;

        public int index;

        public Pair[] buffer;

        public InfoBuffer(int max/* = 60 * 10 */) {
            index = 0;
            bufmax = max;
            buffer = new Pair[bufmax];
            for (int i = 0; i < bufmax; i++) {
                buffer[i] = new Pair(0, null);
            }
        }

        protected void finalize() {
            for (int i = 0; i < bufmax; i++)
                buffer[i].second = null;
            buffer = null;
        }

        public void Clear() {
            for (int i = 0; i < bufmax; i++) {
                buffer[i].first = 0;
                buffer[i].second = null;
            }
        }

        public void addInfo(int pos, DeviceInfo di) {
            if (di != null) {
                buffer[index].first = pos;
                buffer[index].second = di.clone();
                index = (index + 1) % bufmax;
            }
        }

        public DeviceInfo getInfo(int pos) {
            if (pos == -1)
                return buffer[(index + bufmax - 1) % bufmax].second;

            for (int i = (index + bufmax - 1) % bufmax; i != index; i = (i + bufmax - 1) % bufmax)
                if (buffer[i].first <= pos)
                    return buffer[i].second;

            return null;
        }
    }

    public static class BasicTrackInfo extends TrackInfo {

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

        public int getOutput() {
            return output;
        }

        public double getFreqHz() {
            return freq;
        }

        public int getFreq() {
            return _freq;
        }

        public boolean getKeyStatus() {
            return key;
        }

        public int getVolume() {
            return volume;
        }

        public int getMaxVolume() {
            return maxVolume;
        }

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
