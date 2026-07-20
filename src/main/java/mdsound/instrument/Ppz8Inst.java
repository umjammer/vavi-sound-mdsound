package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import mdsound.Instrument;
import mdsound.chips.PPZ8;


public class Ppz8Inst extends Instrument.BaseInstrument {

    private final PPZ8[] chips = { new PPZ8(), new PPZ8() };

    public Ppz8Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "PPZ8";
    }

    @Override
    public String getShortName() {
        return "PPZ8";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].setSamplingRate(samplingRate);
        chips[chipId].reset();
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        return chips[chipId].write(port, adr, data);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        // none
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    public void initialize(int chipId) {
        chips[chipId].init();
    }

    public void makeVolumeTable(int chipId, int vol) {
        chips[chipId].makeVolumeTable(vol);
    }

    public void playPcm(int chipId, int al, int dx) {
        chips[chipId].playPcm(al, dx);
    }

    public void stopPcm(int chipId, int al) {
        chips[chipId].stopPCM(al);
    }

    public void readStatus(int chipId, int al) {
        chips[chipId].readStatus(al);
    }

    public void setVolume(int chipId, int al, int dx) {
        chips[chipId].setVolume(al, dx);
    }

    public void setFrequency(int chipId, int al, int dx, int cx) {
        chips[chipId].setFrequency(al, dx, cx);
    }

    public void setLoopPoint(int chipId, int al, int lpStOfsDX, int lpStOfsCX, int lpEdOfsDI, int lpEdOfsSI) {
        chips[chipId].setLoopPoint(al, lpStOfsDX, lpStOfsCX, lpEdOfsDI, lpEdOfsSI);
    }

    public void stopInterrupt(int chipId) {
        chips[chipId].stopInterrupt();
    }

    public void setPan(int chipId, int al, int dx) {
        chips[chipId].setPan(al, dx);
    }

    public void setSrcFrequency(int chipId, int al, int dx) {
        chips[chipId].setSrcFrequency(al, dx);
    }

    public void setAllVolume(int chipId, int vol) {
        chips[chipId].setAllVolume(vol);
    }

    public void setVolume(int chipId, int vol) {
        chips[chipId].setVolume(vol);
    }

    public void setAdpcmEmu(int chipId, int al) {
        chips[chipId].setAdpcmEmu(al);
    }

    public void setReleaseFlag(int chipId, int v) {
        chips[chipId].setReleaseFlag(v);
    }

    public int convertAdpcmToPcm(int chipId, byte bank) {
        return chips[chipId].convertPviAdpcmToPziPcm(bank);
    }

    // ----

    public synchronized void writePcm(int chipId, int address, int data, byte[][] pcmData) {
        chips[chipId].loadPcm(address, data, pcmData);
    }

    private synchronized Map<String, Object> getInfo(int chipId) {
        PPZ8.Channel[] channels = chips[chipId].getChannels();

        Map<String, Object> info = new HashMap<>();
        for (int ch = 0; ch < 8; ch++) {
            if (channels.length < ch + 1) continue;
            if (channels[ch] == null) continue;

            info.put("channels." + ch + ".pan", channels[ch].pan);

            info.put("channels." + ch + ".keyOn", channels[ch].KeyOn);
            info.put("channels." + ch + ".volume", channels[ch].volume);

            info.put("channels." + ch + ".srcFreq", channels[ch].srcFrequency);
            info.put("channels." + ch + ".freq", channels[ch].frequency);

            info.put("channels." + ch + ".frequency", channels[ch].frequency);
            info.put("channels." + ch + ".playing", channels[ch].playing);

            info.put("channels." + ch + ".dda", channels[ch].bank != 0);
            info.put("channels." + ch + ".flg16", channels[ch].num);

            info.put("channels." + ch + ".sadr", channels[ch].ptr);
            info.put("channels." + ch + ".eadr", channels[ch].end);
            info.put("channels." + ch + ".ladr", channels[ch].loopStartOffset);
            info.put("channels." + ch + ".leadr", channels[ch].loopEndOffset);
            info.put("channels." + ch + ".volumeRL", channels[ch].volume);
            info.put("channels." + ch + ".volumeRR", channels[ch].pan);
        }

        return info;
    }

    // ----

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "info" -> result.putAll(getInfo(chipId));
        }
        return result;
    }
}
