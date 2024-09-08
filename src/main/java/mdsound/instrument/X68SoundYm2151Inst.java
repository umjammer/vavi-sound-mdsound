package mdsound.instrument;

import java.util.function.BiConsumer;

import mdsound.Instrument;
import mdsound.x68sound.X68Sound;
import mdsound.x68sound.SoundIocs;


public class X68SoundYm2151Inst extends Instrument.BaseInstrument {

    private static final int DefaultYM2151ClockValue = 4000000; // X68000 clock

    @Override
    public String getName() {
        return "YM2151x68sound";
    }

    @Override
    public String getShortName() {
        return "OPMx";
    }

    @Override
    public int start(int chipId, int clock) {
        return start(chipId, clock, DefaultYM2151ClockValue, (Object[]) null);
    }

    @Override
    public int start(int chipId, int sampleRate, int clockValue, Object... option) {
        if (chipId > 1)
            return 0;

        x68sound[chipId] = new X68Sound();
        sound_Iocs[chipId] = new SoundIocs(x68sound[chipId]);

        if (option != null) {
            if (option.length > 0 && option[0] != null)
                opmflag = (int) option[0];
            if (option.length > 1 && option[1] != null)
                adpcmflag = (int) option[1];
            if (option.length > 2 && option[2] != null)
                pcmbuf = (int) option[2];
        }

        x68sound[chipId].startPcm(sampleRate, opmflag, adpcmflag, pcmbuf);
        x68sound[chipId].opmClock(clockValue);

        return sampleRate;
    }

    @Override
    public void stop(int chipId) {
        if (x68sound[chipId] == null)
            return;

        x68sound[chipId].free();

        x68sound[chipId] = null;
        sound_Iocs[chipId] = null;
    }

    @Override
    public void reset(int chipId) {
        if (x68sound[chipId] == null)
            return;

        x68sound[chipId].reset();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        if (x68sound[chipId] == null)
            return;

        for (int i = 0; i < samples; i++) {
            x68sound[chipId].getPcm(buf[chipId], 0, samples * 2);
            outputs[0][i] = buf[chipId][0];
            outputs[1][i] = buf[chipId][1];
        }
    }

    private void update(int chipId, int[][] outputs, int samples, BiConsumer<Runnable, Boolean> oneFrameProc) {
        if (x68sound[chipId] == null)
            return;

        for (int i = 0; i < samples; i++) {
            x68sound[chipId].getPcm(buf[chipId], 0, samples * 2, oneFrameProc);
            outputs[0][i] = buf[chipId][0];
            outputs[1][i] = buf[chipId][1];
        }
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        if (x68sound[chipId] == null)
            return 0;
        sound_Iocs[chipId].opmSet(adr, data);
        return 0;
    }

    public X68Sound[] x68sound = new X68Sound[] {
        null, null
    };

    public SoundIocs[] sound_Iocs = new SoundIocs[] {
        null, null
    };

    private short[][] buf = new short[][] {
        new short[2], new short[2]
    };

    private int opmflag = 1;

    private int adpcmflag = 0;

    private int pcmbuf = 5;
}
