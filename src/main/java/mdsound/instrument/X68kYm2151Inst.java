package mdsound.instrument;

import java.util.function.BiConsumer;

import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.x68sound.SoundIocs;
import mdsound.x68sound.X68Sound;


/** X68kYm2151 OPM and PCM8 */
public class X68kYm2151Inst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

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
        return "YM2151x68k";
    }

    @Override
    public String getShortName() {
        return "OPMx";
    }

    // w/o this cause npe of memory at x68000.Global
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
            chips[chipId].getPcm(buf[chipId], 0, samples * 2); // calls opm
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

    @Override
    public void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        chips[chipId].mountMemory(buf);
    }

    /** for render */
    public int getPcm(int chipId, short[] buffer, int offset, int length, BiConsumer<Runnable, Boolean> clock) {
        return chips[chipId].getPcm(buffer, offset, length, clock);
    }

    public int getPcm(int chipId, short[] buffer, int offset, int length) {
        return chips[chipId].getPcm(buffer, offset, length);
    }

    public int start(int chipId, int sampleRate, int opmFlag, int adpcmFlag, int betw, int pcmBuf, int late, double rev) {
        return chips[chipId].start(sampleRate, opmFlag, adpcmFlag, betw, pcmBuf, late, rev);
    }

    public int startPcm(int chipId, int sampleRate, int opmFlag, int adpcmFlag, int pcmBuf) {
        return chips[chipId].startPcm(sampleRate, opmFlag, adpcmFlag, pcmBuf);
    }

    public void initIocs(int chipId) {
        soundIocs[chipId].init();
    }

    public void opmInt(int chipId, Runnable func) {
        chips[chipId].opmInt(func);
    }

    public int opmWait(int chipId, int wait) {
        return chips[chipId].opmWait(wait);
    }

    public int totalVolume(int chipId, int vol) {
        return chips[chipId].totalVolume(vol);
    }

    public void free(int chipId) {
        chips[chipId].free();
    }

    public void abort(int chipId) {
        chips[chipId].pcm8Abort();
    }

    // write
    public void opmSetIocs(int chipId, int addr, int data) {
        soundIocs[chipId].opmSet(addr, data);
    }

    public void keyOnAdpcm(int chipId, int addr, int mode, int len) {
        soundIocs[chipId].adpcmOut(addr, mode, len);
    }

    public void adpcmMod(int chipId, int mode) {
        soundIocs[chipId].adpcmMod(mode);
    }

    public void keyOn(int chipId, int ch, int addr, int mode, int len) {
        chips[chipId].pcm8Out(ch, null, addr, mode, len);
    }

    public void keyOff(int chipId, int ch) {
        chips[chipId].pcm8Out(ch, null, 0, 0, 0);
    }
}
