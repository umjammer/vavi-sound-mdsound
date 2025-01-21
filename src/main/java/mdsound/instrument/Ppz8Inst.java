package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import mdsound.Instrument;
import mdsound.chips.PPZ8;


public class Ppz8Inst extends Instrument.BaseInstrument {

    private final PPZ8[] chips = { new PPZ8(), new PPZ8() };

    public Ppz8Inst() {
        visVolume = new int[][][] {
                {{0, 0}},
                {{0, 0}}
        };
    }

    @Override
    public String getName() {
        return "Ppz8";
    }

    @Override
    public String getShortName() {
        return "Ppz8";
    }

    @Override
    public void reset(int chipId) {
        PPZ8 chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        PPZ8 chip = chips[chipId];
        chip.setSamplingRate(samplingRate);
        reset(chipId);
        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        PPZ8 chip = chips[chipId];
        return chip.write(port, adr, data);
    }


    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        PPZ8 chip = chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        // none
    }

    public void initialize(int chipId) {
        PPZ8 chip = chips[chipId];
        chip.init();
    }

    public void makeVolumeTable(int chipId, int vol) {
        PPZ8 chip = chips[chipId];
        chip.makeVolumeTable(vol);
    }

    public void playPCM(int chipId, int al, int dx) {
        PPZ8 chip = chips[chipId];
        chip.playPCM(al, dx);
    }

    public void stopPCM(int chipId, int al) {
        PPZ8 chip = chips[chipId];
        chip.stopPCM(al);
    }

    public void readStatus(int chipId, int al) {
        PPZ8 chip = chips[chipId];
        chip.readStatus(al);
    }

    public void setVolume(int chipId, int al, int dx) {
        PPZ8 chip = chips[chipId];
        chip.setVolume(al, dx);
    }

    public void setFrequency(int chipId, int al, int dx, int cx) {
        PPZ8 chip = chips[chipId];
        chip.setFrequency(al, dx, cx);
    }

    public void setLoopPoint(int chipId, int al, int lpStOfsDX, int lpStOfsCX, int lpEdOfsDI, int lpEdOfsSI) {
        PPZ8 chip = chips[chipId];
        chip.setLoopPoint(al, lpStOfsDX, lpStOfsCX, lpEdOfsDI, lpEdOfsSI);
    }

    public void stopInterrupt(int chipId) {
        PPZ8 chip = chips[chipId];
        chip.stopInterrupt();
    }

    public void setPan(int chipId, int al, int dx) {
        PPZ8 chip = chips[chipId];
        chip.setPan(al, dx);
    }

    public void setSrcFrequency(int chipId, int al, int dx) {
        PPZ8 chip = chips[chipId];
        chip.setSrcFrequency(al, dx);
    }

    public void setAllVolume(int chipId, int vol) {
        PPZ8 chip = chips[chipId];
        chip.setAllVolume(vol);
    }

    public void setVolume(int chipId, int vol) {
        PPZ8 chip = chips[chipId];
        chip.setVolume(vol);
    }

    public void setAdpcmEmu(int chipId, int al) {
        PPZ8 chip = chips[chipId];
        chip.setAdpcmEmu(al);
    }

    public void setReleaseFlag(int chipId, int v) {
        PPZ8 chip = chips[chipId];
        chip.setReleaseFlag(v);
    }

    public int convertAdpcmToPcm(int chipId, byte bank) {
        PPZ8 chip = chips[chipId];
        return chip.convertPviAdpcmToPziPcm(bank);
    }

    // ----

    public synchronized void writePcm(int chipId, int address, int data, byte[][] pcmData) {
        PPZ8 chip = chips[chipId];
        chip.loadPcm(address, data, pcmData);
    }

    public synchronized PPZ8.Channel[] readStatus(int chipId) {
        PPZ8 chip = chips[chipId];
        return chip.getChannels();
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
