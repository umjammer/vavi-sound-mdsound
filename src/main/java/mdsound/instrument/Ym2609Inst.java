/*
 * GPLv3
 *
 * https://github.com/kuma4649/MDSound
 */

package mdsound.instrument;

import java.util.function.Function;

import dotnet4j.io.Stream;
import mdsound.Instrument;
import mdsound.fmvgen.OPNA2;


/**
 * A virtual instrument.
 *
 * @see "https://ja.wikipedia.org/wiki/%E6%A2%85%E6%9C%AC%E7%AB%9C"
 */
public class Ym2609Inst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 8000000;

    private final OPNA2[] chips = {new OPNA2(), new OPNA2()};

    // TODO
    private boolean visVol;

    private final int[][] keyOn = {new int[12 + 12 + 3 + 1], new int[28]};

    public Ym2609Inst() {
        // 0..Main 1..FM 2..SSG 3..Rhm 4..PCM
        visVolume = new int[][][] {
                {{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}},
                {{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}}
        };
    }

    @Override
    public String getName() {
        return "YM2609";
    }

    @Override
    public String getShortName() {
        return "OPNA2";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        chips[chipId].init(samplingRate);

        if (option != null && option.length > 0 && option[0] instanceof Function) { //<String, Stream>
            if (option[0] instanceof Function) // <String, Stream>
                chips[chipId].init(clock, samplingRate, false, (Function<String, Stream>) option[0], null, 0);
            else if (option[0] instanceof String)
                chips[chipId].init(clock, samplingRate, false, (String) option[0]);
        } else {
            chips[chipId].init(clock, samplingRate);
        }

        return samplingRate;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        assert chipId < chips.length;
        chips[chipId].setReg(port * 0x100 + adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        int[] updateBuffer = chips[chipId].update();
//        for (int i = 0; i < 1; i++) {
//            outputs[0][i] = updateBuffer[i * 2 + 0];
//            outputs[1][i] = updateBuffer[i * 2 + 1];
//
//            //rev[chipId].StoreData(0, (outputs[0][i] + outputs[1][i]) / 2);
//        }
        outputs[0][0] = updateBuffer[0];
        outputs[1][0] = updateBuffer[1];

        if (!visVol) return;

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
        visVolume[chipId][1][0] = chips[chipId].visVolume[0];
        visVolume[chipId][1][1] = chips[chipId].visVolume[1];
        visVolume[chipId][2][0] = chips[chipId].psg.visVolume;
        visVolume[chipId][2][1] = chips[chipId].psg.visVolume;
        visVolume[chipId][3][0] = chips[chipId].visRtmVolume[0];
        visVolume[chipId][3][1] = chips[chipId].visRtmVolume[1];
        visVolume[chipId][4][0] = chips[chipId].visAPCMVolume[0];
        visVolume[chipId][4][1] = chips[chipId].visAPCMVolume[1];
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public void setMask(int chipId, int ch) {
    }

    @Override
    public void resetMask(int chipId, int ch) {
    }

    private void setFMVolume(int chipId, int db) {
        assert chipId < chips.length;
        chips[chipId].setVolumeFM(db);
    }

    private void setPSGVolume(int chipId, int db) {
        assert chipId < chips.length;
        chips[chipId].setVolumePSG(db);
    }

    private void setRhythmVolume(int chipId, int db) {
        assert chipId < chips.length;
        chips[chipId].setVolumeRhythmTotal(db);
    }

    private void setAdpcmVolume(int chipId, int db) {
        assert chipId < chips.length;
        chips[chipId].setVolumeADPCM(db);
    }

    //----

    public synchronized int[] readKeyOn(int chipId) {
        for (int i = 0; i < 11; i++) {
            //ym2608Key[chipId][i] = chips[chipId].CHANNEL[i].KeyOn;
        }
        return keyOn[chipId];
    }

    public synchronized void writeAdpcmA(int chipId, byte[] Buf) {
        assert chipId < chips.length;
        chips[chipId].setAdpcmA(Buf, Buf.length);
    }

    // TODO automatic wired, use annotation?
    public void setFMVolume(int vol, double ignored) {
        setFMVolume(0, vol);
        setFMVolume(1, vol);
    }

    // TODO automatic wired, use annotation?
    public void setPSGVolume(int vol, double ignored) {
        setPSGVolume(0, vol);
        setPSGVolume(1, vol);
    }

    // TODO automatic wired, use annotation?
    public void setRhythmVolume(int vol, double ignored) {
        setRhythmVolume(0, vol);
        setRhythmVolume(1, vol);
    }

    // TODO automatic wired, use annotation?
    public void setAdpcmVolume(int vol, double ignored) {
        setAdpcmVolume(0, vol);
        setAdpcmVolume(1, vol);
    }
}
