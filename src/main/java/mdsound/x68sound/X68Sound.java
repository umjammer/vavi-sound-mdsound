package mdsound.x68sound;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;


public class X68Sound {

    public static final int SNDERR_PCMOUT = -1;
    public static final int SNDERR_TIMER = -2;
    public static final int SNDERR_MEMORY = -3;
    public static final int SNDERR_NOTACTIVE = -4;
    public static final int SNDERR_ALREADYACTIVE = -5;
    public static final int SNDERR_BADARG = -6;
    public static final int SNDERR_DLL = -1;
    public static final int SNDERR_FUNC = -2;

    Global global = null;

    public Opm opm;

    public X68Sound() {
        global = new Global();
        opm = new Opm(global);
        global.opm = opm;
    }

    public void MountMemory(byte[] mem) {
        global.mountMemory(mem);
    }

    public int start(int samprate /*= 44100*/, int opmflag/* = 1*/, int adpcmflag/* = 1*/,
                     int betw /*= 5*/, int pcmbuf /*= 5*/, int late/* = 200*/, double rev/* = 1.0*/) {
        return opm.start(samprate, opmflag, adpcmflag, betw, pcmbuf, late, rev);
    }

    public int sampRate(int samprate/*=44100*/) {
        return opm.setSamprate(samprate);
    }

    public int opmClock(int clock) {
        return opm.setOpmClock(clock);
    }

    public void reset() {
        opm.reset();
    }

    public void free() {
        opm.free();
    }

    public void betwInt(Runnable proc) {
        opm.betwInt(proc);
    }

    public int startPcm(int samprate/* = 44100*/, int opmflag/* = 1*/, int adpcmflag/* = 1*/, int pcmbuf/* = 5*/) {
        return opm.startPcm(samprate, opmflag, adpcmflag, pcmbuf);
    }

    public int getPcm(short[] buf, int offset, int len) {
        return getPcm(buf, offset, len, null);
    }

    public int getPcm(short[] buf, int offset, int len, BiConsumer<Runnable, Boolean> oneFrameProc) {
        return opm.getPcm(buf, offset, len, oneFrameProc);
    }

    public byte opmPeek() {
        return opm.opmPeek();
    }

    public void opmReg(byte no) {
        opm.opmReg(no);
    }

    public void opmPoke(byte data) {
        opm.opmPoke(data);
    }

    public void opmInt(Runnable proc) {
        opm.opmInt(proc);
    }

    public int opmWait(int wait) {
        return opm.setOpmWait(wait);
    }

    public byte adpcmPeek() {
        return opm.adpcmPeek();
    }

    public void adpcmPoke(byte data) {
        opm.adpcmPoke(data);
    }

    public byte ppiPeek() {
        return opm.ppiPeek();
    }

    public void ppiPoke(byte data) {
        opm.ppiPoke(data);
    }

    public void ppiCtrl(byte data) {
        opm.ppiCtrl(data);
    }

    public byte dmaPeek(byte adrs) {
        return opm.dmaPeek(adrs);
    }

    public void dmaPoke(byte adrs, byte data) {
        opm.dmaPoke(adrs, data);
    }

    public void dmaPokeW(byte adrs, int data) {
        opm.dmaPoke(adrs, (byte) (data >> 8));
        opm.dmaPoke((byte) (adrs + 1), (byte) data);
    }

    public void dmaPokeL(byte adrs, int dataPtr) {
        opm.dmaPoke(adrs, (byte) (dataPtr >> 24));
        opm.dmaPoke((byte) (adrs + 1), (byte) (dataPtr >> 16));
        opm.dmaPoke((byte) (adrs + 2), (byte) (dataPtr >> 8));
        opm.dmaPoke((byte) (adrs + 3), (byte) (dataPtr));
    }

    public void y(byte no, byte data) {
        opm.opmReg(no);
        opm.opmPoke(data);
    }

    public void dmaInt(Runnable proc) {
        opm.dmaInt(proc);
    }

    public void dmaErrInt(Runnable proc) {
        opm.dmaErrInt(proc);
    }

    public void memReadFunc(Function<Integer, Integer> func) {
        opm.memReadFunc(func);
    }

    public void waveFunc(Supplier<Integer> func) {
        opm.setWaveFunc(func);
    }

    public int pcm8Out(int ch, byte[] adrsBuf, int adrsPtr, int mode, int len) {
        return opm.pcm8Out(ch, adrsBuf, adrsPtr, mode, len);
    }

    public int pcm8Aot(int ch, byte[] tblBuf, int tblPtr, int mode, int cnt) {
        return opm.pcm8Aot(ch, tblBuf, tblPtr, mode, cnt);
    }

    public int pcm8_Lot(int ch, byte[] tblBuf, int tblPtr, int mode) {
        return opm.pcm8Lot(ch, tblBuf, tblPtr, mode);
    }

    public int pcm8SetMode(int ch, int mode) {
        return opm.pcm8SetMode(ch, mode);
    }

    public int pcm8GetRest(int ch) {
        return opm.pcm8GetRest(ch);
    }

    public int pcm8GetMode(int ch) {
        return opm.pcm8GetMode(ch);
    }

    public int pcm8Abort() {
        return opm.pcm8Abort();
    }


    public int totalVolume(int v) {
        return opm.setTotalVolume(v);
    }

    public void setMask(int v) {
        opm.setMask(v);
    }


    public int errorCode() {
        return global.ErrorCode;
    }

    public int debugValue() {
        return global.DebugValue;
    }

    public void timerA() {
        opm.csmKeyOn();
    }
}

