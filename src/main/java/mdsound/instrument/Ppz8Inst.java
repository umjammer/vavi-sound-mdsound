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

    public synchronized PPZ8.Channel[] readStatus(int chipId) {
        return chips[chipId].getChannels();
    }

    // ----

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        }
        return result;
    }
}
