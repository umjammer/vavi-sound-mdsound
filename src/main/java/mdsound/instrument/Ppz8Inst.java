package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import mdsound.Instrument;
import mdsound.chips.PPZ8Status;


public class Ppz8Inst extends Instrument.BaseInstrument {
    @Override
    public String getName() {
        return "Ppz8Inst";
    }

    @Override
    public String getShortName() {
        return "Ppz8Inst";
    }

    public Ppz8Inst() {
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void reset(byte chipId) {
        PPZ8Status chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, clock, 0);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        PPZ8Status chip = chips[chipId];
        chip.setSamplingRate(clock);
        reset(chipId);
        return clock;
    }

    @Override
    public void stop(byte chipId) {
        //none
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
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

    private void playPCM(int chipId, byte al, int dx) {
        PPZ8Status chip = chips[chipId];
        chip.playPCM(al, dx);
    }

    private void stopPCM(int chipId, byte al) {
        PPZ8Status chip = chips[chipId];
        chip.stopPCM(al);
    }

    public int loadPcm(int chipId, byte bank, byte mode, byte[][] pcmData) {
        PPZ8Status chip = chips[chipId];
        return chip.loadPcm(bank, mode, pcmData);
    }

    private void readStatus(int chipId, byte al) {
        PPZ8Status chip = chips[chipId];
        chip.readStatus(al);
    }

    private void setVolume(int chipId, byte al, int dx) {
        PPZ8Status chip = chips[chipId];
        chip.setVolume(al, dx);
    }

    private void setFrequency(int chipId, byte al, int dx, int cx) {
        PPZ8Status chip = chips[chipId];
        chip.setFrequency(al, dx, cx);
    }

    private void setLoopPoint(int chipId, byte al, int lpStOfsDX, int lpStOfsCX, int lpEdOfsDI, int lpEdOfsSI) {
        PPZ8Status chip = chips[chipId];
        chip.setLoopPoint(al, lpStOfsDX, lpStOfsCX, lpEdOfsDI, lpEdOfsSI);
    }

    private void stopInterrupt(int chipId) {
        PPZ8Status chip = chips[chipId];
        chip.stopInterrupt();
    }

    private void setPan(int chipId, byte al, int dx) {
        PPZ8Status chip = chips[chipId];
        chip.setPan(al, dx);
    }

    private void setSrcFrequency(int chipId, byte al, int dx) {
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

    private void setAdpcmEmu(int chipId, byte al) {
        PPZ8Status chip = chips[chipId];
        chip.setAdpcmEmu(al);
    }

    private void setReleaseFlag(int chipId, int v) {
        PPZ8Status chip = chips[chipId];
        chip.setReleaseFlag(v);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        PPZ8Status chip = chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private int convertPviAdpcmToPziPcm(int chipId, byte bank) {
        PPZ8Status chip = chips[chipId];
        return chip.convertPviAdpcmToPziPcm(bank);
    }

    public PPZ8Status.Channel[] getPPZ8State(byte chipId) {
        PPZ8Status chip = chips[chipId];
        return chip.getChannels();
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("ppz8", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
