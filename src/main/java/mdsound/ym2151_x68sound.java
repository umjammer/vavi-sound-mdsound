
package mdsound;

import java.util.function.BiConsumer;

import mdsound.x68Sound.X68Sound;
import mdsound.x68Sound.sound_iocs;


public class ym2151_x68sound extends Instrument {
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
    public int Start(byte ChipID, int clock) {
        return Start(ChipID, clock, DefaultYM2151ClockValue, (Object[]) null);
    }

    @Override
    public int Start(byte ChipID, int sampleRate, int ClockValue, Object... option) {
        if (ChipID > 1)
            return 0;

        x68sound[ChipID] = new X68Sound();
        sound_Iocs[ChipID] = new sound_iocs(x68sound[ChipID]);

        if (option != null) {
            if (option.length > 0 && option[0] != null)
                opmflag = (int) option[0];
            if (option.length > 1 && option[1] != null)
                adpcmflag = (int) option[1];
            if (option.length > 2 && option[2] != null)
                pcmbuf = (int) option[2];
        }

        x68sound[ChipID].X68Sound_StartPcm((int) sampleRate, opmflag, adpcmflag, pcmbuf);
        x68sound[ChipID].X68Sound_OpmClock((int) ClockValue);

        return sampleRate;
    }

    @Override
    public void Stop(byte ChipID) {
        if (x68sound[ChipID] == null)
            return;

        x68sound[ChipID].X68Sound_Free();

        x68sound[ChipID] = null;
        sound_Iocs[ChipID] = null;
    }

    @Override
    public void Reset(byte ChipID) {
        if (x68sound[ChipID] == null)
            return;

        x68sound[ChipID].X68Sound_Reset();
    }

    @Override
    public void Update(byte ChipID, int[][] outputs, int samples) {
        if (x68sound[ChipID] == null)
            return;

        for (int i = 0; i < samples; i++) {
            x68sound[ChipID].X68Sound_GetPcm(buf[ChipID], 0, samples * 2);
            outputs[0][i] = buf[ChipID][0];
            outputs[1][i] = buf[ChipID][1];
        }
    }

    public void Update(byte ChipID, int[][] outputs, int samples, BiConsumer<Runnable, Boolean> oneFrameproc) {
        if (x68sound[ChipID] == null)
            return;

        for (int i = 0; i < samples; i++) {
            x68sound[ChipID].X68Sound_GetPcm(buf[ChipID], 0, samples * 2, oneFrameproc);
            outputs[0][i] = buf[ChipID][0];
            outputs[1][i] = buf[ChipID][1];
        }
    }

    @Override
    public int Write(byte ChipID, int port, int adr, int data) {
        if (x68sound[ChipID] == null)
            return 0;
        sound_Iocs[ChipID]._iocs_opmset(adr, data);
        return 0;
    }

    public X68Sound[] x68sound = new X68Sound[] {
        null, null
    };

    public sound_iocs[] sound_Iocs = new sound_iocs[] {
        null, null
    };

    private short[][] buf = new short[][] {
        new short[2], new short[2]
    };

    private int opmflag = 1;

    private int adpcmflag = 0;

    private int pcmbuf = 5;

}
