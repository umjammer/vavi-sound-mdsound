package mdsound.instrument;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.WSwan;


public class WSwanInst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 3072000;

    private int masterClock = DefaultClockValue;
    private int sampleRate = 44100;

    private final WSwan[] chips = {new WSwan(DefaultClockValue), new WSwan(DefaultClockValue)};

    final int[] mask = {0, 0};

    @Override
    public String getName() {
        return "WonderSwan";
    }

    @Override
    public String getShortName() {
        return "WSwan";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].ws_audio_reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].init(samplingRate, clock);
        sampleRate = samplingRate;
        masterClock = clock;

        visVolume = new int[2][][];
        visVolume[0] = new int[2][];
        visVolume[1] = new int[2][];
        visVolume[0][0] = new int[2];
        visVolume[1][0] = new int[2];
        visVolume[0][1] = new int[2];
        visVolume[1][1] = new int[2];

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].writeAudioPort(adr + 0x80, data);
        return 0;
    }

    private double sampleCounter = 0;
    // TODO is thread safe?
    private final int[][] frm = {new int[1], new int[1]};
    private final int[][] before = {new int[1], new int[1]};

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;

            sampleCounter += (masterClock / 128.0) / sampleRate;
            int upc = (int) sampleCounter;
            while (sampleCounter >= 1) {
                chips[chipId].update(1, frm);

                outputs[0][i] += frm[0][0];
                outputs[1][i] += frm[1][0];

                sampleCounter -= 1.0;
            }

            if (upc != 0) {
                outputs[0][i] /= upc;
                outputs[1][i] /= upc;
                before[0][i] = outputs[0][i];
                before[1][i] = outputs[1][i];
            } else {
                outputs[0][i] = before[0][i];
                outputs[1][i] = before[1][i];
            }

            outputs[0][i] <<= 2;
            outputs[1][i] <<= 2;
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        chips[chipId].stop();
    }

    private void setMute(int chipId, int v) {
    }

    // ----

    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= ch;
        setMute(chipId, mask[chipId]);
    }

    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~ch;
        setMute(chipId, mask[chipId]);
    }

    public synchronized void writeMemory(int chipId, int adr, int data) {
        chips[chipId].writeRamByte(adr, data);
    }

    public void setVolume(int vol) {
        // TODO
//        c.volume = Math.max(Math.min(vol, 20), -192);
//        //int n = (((int)(16384.0 * Math.pow(10.0, c.Volume / 40.0)) * c.tVolumeBalance) >> 8) / chips.length;
//        int n = (((int) (16384.0 * Math.pow(10.0, c.volume / 40.0)) * c.tVolumeBalance) >> 8);
//        //16384 = 0x4000 = short.MAXValue + 1
//        c.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }
}
