
package mdsound;

import java.util.function.BiConsumer;

import mdsound.x68sound.X68Sound;
import mdsound.x68sound.SoundIocs;


public class Ym2151X68Sound extends Instrument.BaseInstrument {
    private static final int DefaultYM2151ClockValue = 4000000;// X68000 clock

    @Override
    public String getName() {
        return "YM2151x68sound";
    }

    @Override
    public String getShortName() {
        return "OPMx";
    }

    @Override
    public int start(byte chipID, int clock) {
        return start(chipID, clock, DefaultYM2151ClockValue, (Object[]) null);
    }

    @Override
    public int start(byte chipID, int sampleRate, int clockValue, Object... option) {
        if (chipID > 1)
            return 0;

        x68sound[chipID] = new X68Sound();
        sound_Iocs[chipID] = new SoundIocs(x68sound[chipID]);

        if (option != null) {
            if (option.length > 0 && option[0] != null)
                opmflag = (int) option[0];
            if (option.length > 1 && option[1] != null)
                adpcmflag = (int) option[1];
            if (option.length > 2 && option[2] != null)
                pcmbuf = (int) option[2];
        }

        x68sound[chipID].startPcm(sampleRate, opmflag, adpcmflag, pcmbuf);
        x68sound[chipID].opmClock(clockValue);

        return sampleRate;
    }

    @Override
    public void stop(byte chipID) {
        if (x68sound[chipID] == null)
            return;

        x68sound[chipID].free();

        x68sound[chipID] = null;
        sound_Iocs[chipID] = null;
    }

    @Override
    public void reset(byte chipID) {
        if (x68sound[chipID] == null)
            return;

        x68sound[chipID].reset();
    }

    @Override
    public void update(byte chipID, int[][] outputs, int samples) {
        if (x68sound[chipID] == null)
            return;

        for (int i = 0; i < samples; i++) {
            x68sound[chipID].getPcm(buf[chipID], 0, samples * 2);
            outputs[0][i] = buf[chipID][0];
            outputs[1][i] = buf[chipID][1];
        }
    }

    private void update(byte chipID, int[][] outputs, int samples, BiConsumer<Runnable, Boolean> oneFrameProc) {
        if (x68sound[chipID] == null)
            return;

        for (int i = 0; i < samples; i++) {
            x68sound[chipID].getPcm(buf[chipID], 0, samples * 2, oneFrameProc);
            outputs[0][i] = buf[chipID][0];
            outputs[1][i] = buf[chipID][1];
        }
    }

    @Override
    public int write(byte chipID, int port, int adr, int data) {
        if (x68sound[chipID] == null)
            return 0;
        sound_Iocs[chipID].opmSet(adr, data);
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
