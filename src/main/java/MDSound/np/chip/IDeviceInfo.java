
package MDSound.np.chip;

public class IDeviceInfo {
    public IDeviceInfo Clone() {
        return null;
    }
}

class ITrackInfo extends IDeviceInfo {
    public IDeviceInfo Clone() {
        return null;
    }

    // 現在の出力値をそのまま返す
    public int GetOutput() {
        return 0;
    }

    // 周波数をHzで返す
    public double GetFreqHz() {
        return 0;
    }

    // 周波数をデバイス依存値で返す．
    public int GetFreq() {
        return 0;
    }

    // 音量を返す
    public int GetVolume() {
        return 0;
    }

    // 音量の最大値を返す
    public int GetMaxVolume() {
        return 0;
    }

    // 発音中ならtrue OFFならfalse
    public boolean GetKeyStatus() {
        return false;
    }

    // トーン番号
    public int GetTone() {
        return 0;
    }

    // 周波数をノート番号に変換．0x60がo4c 0は無効
    public int GetNote(double freq) {
        final double LOG2_440 = 8.7813597135246596040696824762152;// ln(440) /
                                                                  // ln(2)
        final double LOG_2 = 0.69314718055994530941723212145818;// ln(2)
        final int NOTE_440HZ = 0x69;

        if (freq > 1.0)
            return (int) ((12 * (Math.log(freq) / LOG_2 - LOG2_440) + NOTE_440HZ + 0.5));
        else
            return 0;
    }
}

/* TrackInfo を バッファリング */
class InfoBuffer {
    public class pair {
        public int first;

        public IDeviceInfo second;
    }

    public int bufmax;

    public int index;

    public pair[] buffer;

    public InfoBuffer(int max/* = 60 * 10 */) {
        index = 0;
        bufmax = max;
        buffer = new pair[bufmax];
        for (int i = 0; i < bufmax; i++) {
            buffer[i] = new pair();
            buffer[i].first = 0;
            buffer[i].second = null;
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

    public void AddInfo(int pos, IDeviceInfo di) {
        if (di != null) {
            buffer[index].first = pos;
            buffer[index].second = di.Clone();
            index = (index + 1) % bufmax;
        }
    }

    public IDeviceInfo GetInfo(int pos) {
        if (pos == -1)
            return buffer[(index + bufmax - 1) % bufmax].second;

        for (int i = (index + bufmax - 1) % bufmax; i != index; i = (i + bufmax - 1) % bufmax)
            if (buffer[i].first <= pos)
                return buffer[i].second;

        return null;
    }
}

class TrackInfoBasic extends ITrackInfo {
    public int output;

    public int volume;

    public int max_volume;

    public int _freq;

    public double freq;

    public int freqp;

    public boolean halt;

    public boolean key;

    public int tone;

    public int freqshift;

    public IDeviceInfo Clone() {
        TrackInfoBasic tib = new TrackInfoBasic();
        tib.output = output;
        tib.volume = volume;
        tib.max_volume = max_volume;
        tib._freq = _freq;
        tib.freq = freq;
        tib.key = key;
        tib.tone = tone;
        return tib;
    }

    public int GetOutput() {
        return output;
    }

    public double GetFreqHz() {
        return freq;
    }

    public int GetFreq() {
        return _freq;
    }

    public boolean GetKeyStatus() {
        return key;
    }

    public int GetVolume() {
        return volume;
    }

    public int GetMaxVolume() {
        return max_volume;
    }

    public int GetTone() {
        return tone;
    }

    public boolean GetHalt() {
        return halt;
    }

    public int GetFreqp() {
        return freqp;
    }

    public int GetFreqShift() {
        return freqshift;
    }
};
