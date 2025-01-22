
package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.C352;


public class C352Inst extends Instrument.BaseInstrument {

    public static final int MAX_CHIPS = 0x02;

    private final C352[] chips = {new C352(), new C352()};

    public C352Inst() {
        visVolume = new int[][][] {
                // 0..Main
                {{0, 0}},
                {{0, 0}}
        };
    }

    @Override
    public String getName() {
        return "C352";
    }

    @Override
    public String getShortName() {
        return "C352";
    }

    @Override
    public void reset(int chipId) {
        C352 chip = chips[chipId];
        chip.reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... Option) {
        assert chipId < MAX_CHIPS;

        int clockDivider;
        if (Option == null || Option.length < 1) clockDivider = 0;
        else clockDivider = (int) Option[0];

        C352 chip = chips[chipId];
        return chip.start(clock, clockDivider * 4);
    }

    @Override
    public int read(int chipId, int adr) {
        C352 chip = chips[chipId];
        return chip.read(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        C352 chip = chips[chipId];
        chip.write(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        C352 chip = chips[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
        C352 chip = chips[chipId];
        chip.stop();
    }

    public void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        writePcm(chipId, romSize, dataStart, dataLength, romData, 0);
    }

    public void setMuteMask(int chipId, int muteMask) {
        C352 chip = chips[chipId];
        chip.setMuteMask(muteMask);
    }

    public int getMuteMask(int chipId) {
        C352 chip = chips[chipId];
        return chip.getMuteMask();
    }

    //----

    public synchronized void writePcm(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAdr) {
        C352 chip = chips[chipId];
        chip.writeRom(romSize, dataStart, dataLength, romData, srcStartAdr);
    }

    public synchronized int[] readFlags(int chipId) {
        C352 chip = chips[chipId];
        return chip.getFlags();
    }

    /**
     * used for volume also
     * @see mdsound.MDSound.Chip.SetVolume
     */
    public void setRearMute(int vol, double ignored) {
        C352.setOptions(vol & 0xff); // TODO ugly
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x40, 8d);
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
