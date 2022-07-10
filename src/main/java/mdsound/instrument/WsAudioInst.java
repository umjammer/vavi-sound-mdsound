package mdsound.instrument;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.WsAudio;


public class WsAudioInst extends Instrument.BaseInstrument {

    public static final int DefaultWSwanClockValue = 3072000;

    private int masterClock = DefaultWSwanClockValue;
    private int sampleRate = 44100;

    private WsAudio[] chip = new WsAudio[] {new WsAudio(DefaultWSwanClockValue), new WsAudio(DefaultWSwanClockValue)};

    @Override
    public String getName() {
        return "WonderSwan";
    }

    @Override
    public String getShortName() {
        return "WSwan";
    }

    @Override
    public void reset(byte chipId) {
        chip[chipId].ws_audio_reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, clock, DefaultWSwanClockValue);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        chip[chipId].init(clock, clockValue);
        sampleRate = clock;
        masterClock = clockValue;

        visVolume = new int[2][][];
        visVolume[0] = new int[2][];
        visVolume[1] = new int[2][];
        visVolume[0][0] = new int[2];
        visVolume[1][0] = new int[2];
        visVolume[0][1] = new int[2];
        visVolume[1][1] = new int[2];

        return clock;
    }

    @Override
    public void stop(byte chipId) {
        chip[chipId].stop();
    }

    private double sampleCounter = 0;
    private int[][] frm = new int[][] {new int[1], new int[1]};
    private int[][] before = new int[][] {new int[1], new int[1]};

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;

            sampleCounter += (masterClock / 128.0) / sampleRate;
            int upc = (int) sampleCounter;
            while (sampleCounter >= 1) {
                chip[chipId].update(1, frm);

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
    public int write(byte chipId, int port, int adr, int data) {
        chip[chipId].writeAudioPort((byte) (adr + 0x80), (byte) data);
        return 0;
    }

    public int WriteMem(byte chipId, int adr, int data) {
        chip[chipId].writeRamByte(adr, (byte) data);
        return 0;
    }

    public void SetMute(byte chipId, int v) {
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }
}
