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

    public int start(int samprate /* = 44100 */, int opmflag /* = 1 */, int adpcmflag /* = 1 */,
                     int betw /* = 5 */, int pcmbuf /* = 5 */, int late /* = 200 */, double rev /* = 1.0 */) {
        return opm.start(samprate, opmflag, adpcmflag, betw, pcmbuf, late, rev);
    }

    public int sampRate(int samprate /* = 44100 */) {
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

    public int startPcm(int samprate /* = 44100 */, int opmflag /* = 1 */, int adpcmflag /* = 1 */, int pcmbuf /* = 5 */) {
        return opm.startPcm(samprate, opmflag, adpcmflag, pcmbuf);
    }

    public int getPcm(short[] buf, int offset, int len) {
        return getPcm(buf, offset, len, null);
    }

    public int getPcm(short[] buf, int offset, int len, BiConsumer<Runnable, Boolean> oneFrameProc) {
        return opm.getPcm(buf, offset, len, oneFrameProc);
    }

    public int opmPeek() {
        return opm.opmPeek();
    }

    public void opmReg(int no) {
        opm.opmReg(no);
    }

    public void opmPoke(int data) {
        opm.opmPoke(data);
    }

    public void opmInt(Runnable proc) {
        opm.opmInt(proc);
    }

    public int opmWait(int wait) {
        return opm.setOpmWait(wait);
    }

    public int adpcmPeek() {
        return opm.adpcmPeek();
    }

    public void adpcmPoke(int data) {
        opm.adpcmPoke(data);
    }

    public int ppiPeek() {
        return opm.ppiPeek();
    }

    public void ppiPoke(int data) {
        opm.ppiPoke(data);
    }

    public void ppiCtrl(int data) {
        opm.ppiCtrl(data);
    }

    public int dmaPeek(int adrs) {
        return opm.dmaPeek(adrs);
    }

    public void dmaPoke(int adrs, int data) {
        opm.dmaPoke(adrs, data);
    }

    public void dmaPokeW(int adrs, int data) {
        opm.dmaPoke(adrs, data >> 8);
        opm.dmaPoke(adrs + 1, data);
    }

    public void dmaPokeL(int adrs, int dataPtr) {
        opm.dmaPoke(adrs, dataPtr >> 24);
        opm.dmaPoke(adrs + 1, dataPtr >> 16);
        opm.dmaPoke(adrs + 2, dataPtr >> 8);
        opm.dmaPoke(adrs + 3, dataPtr);
    }

    public void y(int no, int data) {
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

