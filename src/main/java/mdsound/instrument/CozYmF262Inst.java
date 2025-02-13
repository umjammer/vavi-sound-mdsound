package mdsound.instrument;

import java.lang.System.Logger;
import java.util.HashMap;
import java.util.Map;

import de.quippy.opl.OPL3;
import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;

import static java.lang.System.getLogger;


// YmF262 OPL3 cozendey
public class CozYmF262Inst extends Instrument.BaseInstrument {

    private static final Logger logger = getLogger(CozYmF262Inst.class.getName());

    public static final int MAX_CHIPS = 0x02;

    private final OPL3[] chips = {new OPL3(), new OPL3()};

    public CozYmF262Inst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "YMF262cozendey";
    }

    @Override
    public String getShortName() {
        return "Opl3";
    }

    @Override
    public void reset(int chipId) {
        for (int register = 0; register < 256; register++) {
            chips[chipId].write(0, register, 0);
            chips[chipId].write(1, register, 0);
        }
        chips[chipId].write(1, 5, 1);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        return 49716;
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        int adr_ = port * 0x100 + adr;
        writeInternal(chipId, (adr_ & 0x100) != 0 ? 0x02 : 0x00, adr);
        writeInternal(chipId, (adr_ & 0x100) != 0 ? 0x03 : 0x01, data);
        return 0;
    }

    private int address;

    private void writeInternal(int chipId, int adr, int data) {
        switch (adr) {
            case 0, 2 -> { this.address = data; }
            case 1, 3 -> { chips[chipId].write((adr & 2) >> 1, address, data); }
        }
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        int[] outBuffer = new int[4];

        chips[chipId].read(outBuffer, 1);
        for (int i = 0; i < 4; i++)
            outputs[i & 1][0] += outBuffer[i];

//logger.log(Level.TRACE, "output %d %d".formatted(outputs[0][0], outputs[1][0]));
        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public void setMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(ch); // TODO
    }

    @Override
    public void resetMask(int chipId, int ch) {
//        chips[chipId].setMuteMask(~ch); // TODO
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 2d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        }
        return result;
    }
}
