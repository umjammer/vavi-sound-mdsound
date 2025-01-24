package mdsound.instrument;

import java.util.function.BiConsumer;

import mdsound.Instrument;
import mdsound.x68sound.X68Sound;
import mdsound.x68sound.SoundIocs;


public class X68SoundYm2151Inst extends Instrument.BaseInstrument {

    /** X68000 clock */
    public static final int DefaultClockValue = 4000000;

    public final X68Sound[] chips = {new X68Sound(), new X68Sound()};
    public final SoundIocs[] soundIocs = {null, null};

    private int opmFlag = 1;
    private int adpcmFlag = 0;
    private int pcmBuf = 5;

    @Override
    public void reset(int chipId) {
        assert chipId < chips.length;
        chips[chipId].reset();
    }

    @Override
    public String getName() {
        return "YM2151x68sound";
    }

    @Override
    public String getShortName() {
        return "OPMx";
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < chips.length;

        soundIocs[chipId] = new SoundIocs(chips[chipId]);

        if (option != null) {
            if (option.length > 0 && option[0] != null)
                opmFlag = (int) option[0];
            if (option.length > 1 && option[1] != null)
                adpcmFlag = (int) option[1];
            if (option.length > 2 && option[2] != null)
                pcmBuf = (int) option[2];
        }

        chips[chipId].startPcm(samplingRate, opmFlag, adpcmFlag, pcmBuf);
        chips[chipId].opmClock(clock);

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        soundIocs[chipId].opmSet(adr, data);
        return 0;
    }

    // TODO is thread safe?
    private final short[][] buf = {new short[2], new short[2]};

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        assert chipId < chips.length;
        for (int i = 0; i < samples; i++) {
            chips[chipId].getPcm(buf[chipId], 0, samples * 2);
            outputs[0][i] = buf[chipId][0];
            outputs[1][i] = buf[chipId][1];
        }
    }

    @Override
    public void stop(int chipId) {
        assert chipId < chips.length;
        chips[chipId].free();
        soundIocs[chipId] = null;
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    public void update(int chipId, int[][] outputs, int samples, BiConsumer<Runnable, Boolean> oneFrameProc) {
        assert chipId < chips.length;
        for (int i = 0; i < samples; i++) {
            chips[chipId].getPcm(buf[chipId], 0, samples * 2, oneFrameProc);
            outputs[0][i] = buf[chipId][0];
            outputs[1][i] = buf[chipId][1];
        }
    }
}
