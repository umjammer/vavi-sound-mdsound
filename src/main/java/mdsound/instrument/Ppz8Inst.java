package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import mdsound.Instrument;
import mdsound.chips.PPZ8Status;


public class Ppz8Inst extends Instrument.BaseInstrument {

    @Override
    public String getName() {
        return "Ppz8";
    }

    @Override
    public String getShortName() {
        return "Ppz8";
    }

    public Ppz8Inst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void reset(int chipId) {
        PPZ8Status chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, clock, 0);
    }

    @Override
    public int start(int chipId, int clock, int clockValue, Object... option) {
        PPZ8Status chip = chips[chipId];
        chip.setSamplingRate(clock);
        reset(chipId);
        return clock;
    }

    @Override
    public void stop(int chipId) {
        //none
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        PPZ8Status chip = chips[chipId];
        return chip.write(port, adr, data);
    }

    private PPZ8Status[] chips = new PPZ8Status[] { new PPZ8Status(), new PPZ8Status() };

    private void initialize(int chipId) {
        PPZ8Status chip = chips[chipId];
        chip.init();
    }

    private void makeVolumeTable(int chipId, int vol) {
        PPZ8Status chip = chips[chipId];
        chip.makeVolumeTable(vol);
    }

    private void playPCM(int chipId, int al, int dx) {
        PPZ8Status chip = chips[chipId];
        chip.playPCM(al, dx);
    }

    private void stopPCM(int chipId, int al) {
        PPZ8Status chip = chips[chipId];
        chip.stopPCM(al);
    }

    public int loadPcm(int chipId, int bank, int mode, byte[][] pcmData) {
        PPZ8Status chip = chips[chipId];
        return chip.loadPcm(bank, mode, pcmData);
    }

    private void readStatus(int chipId, int al) {
        PPZ8Status chip = chips[chipId];
        chip.readStatus(al);
    }

    private void setVolume(int chipId, int al, int dx) {
        PPZ8Status chip = chips[chipId];
        chip.setVolume(al, dx);
    }

    private void setFrequency(int chipId, int al, int dx, int cx) {
        PPZ8Status chip = chips[chipId];
        chip.setFrequency(al, dx, cx);
    }

    private void setLoopPoint(int chipId, int al, int lpStOfsDX, int lpStOfsCX, int lpEdOfsDI, int lpEdOfsSI) {
        PPZ8Status chip = chips[chipId];
        chip.setLoopPoint(al, lpStOfsDX, lpStOfsCX, lpEdOfsDI, lpEdOfsSI);
    }

    private void stopInterrupt(int chipId) {
        PPZ8Status chip = chips[chipId];
        chip.stopInterrupt();
    }

    private void setPan(int chipId, int al, int dx) {
        PPZ8Status chip = chips[chipId];
        chip.setPan(al, dx);
    }

    private void setSrcFrequency(int chipId, int al, int dx) {
        PPZ8Status chip = chips[chipId];
        chip.setSrcFrequency(al, dx);
    }

    private void setAllVolume(int chipId, int vol) {
        PPZ8Status chip = chips[chipId];
        chip.setAllVolume(vol);
    }

    private void setVolume(int chipId, int vol) {
        PPZ8Status chip = chips[chipId];
        chip.setVolume(vol);
    }

    private void setAdpcmEmu(int chipId, int al) {
        PPZ8Status chip = chips[chipId];
        chip.setAdpcmEmu(al);
    }

    private void setReleaseFlag(int chipId, int v) {
        PPZ8Status chip = chips[chipId];
        chip.setReleaseFlag(v);
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        PPZ8Status chip = chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private int convertPviAdpcmToPziPcm(int chipId, byte bank) {
        PPZ8Status chip = chips[chipId];
        return chip.convertPviAdpcmToPziPcm(bank);
    }

    public PPZ8Status.Channel[] getPPZ8State(int chipId) {
        PPZ8Status chip = chips[chipId];
        return chip.getChannels();
    }

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
