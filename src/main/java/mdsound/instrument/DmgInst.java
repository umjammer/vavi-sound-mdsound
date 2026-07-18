package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.GbSound;


// DMG
public class DmgInst extends Instrument.BaseInstrument {

    public static final int DefaultClockValue = 4194304;
    public static final int MAX_CHIPS = 0x02;

    private final GbSound[] chips = {new GbSound(), new GbSound()};

    public DmgInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "Gameboy DMG";
    }

    @Override
    public String getShortName() {
        return "DMG";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert  chipId < MAX_CHIPS;

        int rate = (clock & 0x7fff_ffff) / 64;
        if (((CHIP_SAMPLING_MODE & 0x01) != 0 && rate < CHIP_SAMPLE_RATE) || CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;

        chips[chipId].start(clock, rate);
        return rate;
    }

    @Override
    public int read(int chipId, int adr) {
        return chips[chipId].readSound(adr);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].writeSound(adr, data);
        return 0;
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        chips[chipId].update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public void stop(int chipId) {
    }

    @Override
    public synchronized void setMask(int chipId, int ch) {
        int maskStatus = chips[chipId].getMuteMask();
        maskStatus |= 1 << ch; // ch:0 - 3
        chips[chipId].setMuteMask(maskStatus);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        int maskStatus = chips[chipId].getMuteMask();
        maskStatus &= ~(1 << ch); // ch:0 - 3
        chips[chipId].setMuteMask(maskStatus);
    }

    public int readPcm(int chipId, int offset) {
        return chips[chipId].readWave(offset);
    }

    public void writePcm(int chipId, int offset, byte data) {
        chips[chipId].writeWave(offset, data);
    }

    //----

    private synchronized Map<String, Object> getInfo(int chipId) {
        GbSound chip = chips[chipId];

        Map<String, Object> info = new HashMap<>();
        // pan
        info.put("channels.0.pan",  (chip.controller.mode1Left * 2) + chip.controller.mode1Right);
        info.put("channels.1.pan",  (chip.controller.mode2Left * 2) + chip.controller.mode2Right);
        info.put("channels.2.pan",  (chip.controller.mode3Left * 2) + chip.controller.mode3Right);
        info.put("channels.3.pan",  (chip.controller.mode4Left * 2) + chip.controller.mode4Right);

        // freq
        info.put("channels.0.freq",  chip.sound1.frequency);
        info.put("channels.1.freq",  chip.sound2.frequency);
        info.put("channels.2.freq",  chip.sound3.frequency);
        info.put("channels.3.freq",  chip.sound4.registers[3] & 0x7); // pfq
        info.put("channels.3.bit.47",  (chip.sound4.registers[3] & 0x8) != 0); // poly
        info.put("channels.3.srcFreq",  (chip.sound4.registers[3] & 0xf0) >> 4); // pc

        // CC
        info.put("channels.0.bit.0",  chip.sound1.lengthEnabled);
        info.put("channels.1.bit.0",  chip.sound2.lengthEnabled);
        info.put("channels.2.bit.0",  chip.sound3.lengthEnabled);
        info.put("channels.3.bit.0",  chip.sound4.lengthEnabled);

        // Ini
        info.put("channels.0.bit.1",  (chip.sound1.registers[4] & 0x80) != 0);
        info.put("channels.1.bit.1",  (chip.sound2.registers[4] & 0x80) != 0);
        info.put("channels.2.bit.1",  (chip.sound3.registers[4] & 0x80) != 0);
        info.put("channels.3.bit.1",  (chip.sound4.registers[4] & 0x80) != 0);

        // Env.Dir
        info.put("channels.0.bit.2",  chip.sound1.envelopeDirection == 1);
        info.put("channels.1.bit.2",  chip.sound2.envelopeDirection == 1);
        //newParam.put("channels.2.bit.2",  nothing);
        info.put("channels.3.bit.2",  chip.sound4.envelopeDirection == 1);

        // Sweep Dec
        info.put("channels.0.bit.3",  chip.sound1.sweepDirection == -1);

        // Env.Spd
        info.put("channels.0.inst.0",  chip.sound1.envelopeTime);
        info.put("channels.1.inst.0",  chip.sound2.envelopeTime);
        //newParam.put("channels.2.inst.0", nothing);
        info.put("channels.3.inst.0",  chip.sound4.envelopeTime);

        // Env.Vol
        info.put("channels.0.inst.1",  chip.sound1.envelopeValue);
        info.put("channels.1.inst.1",  chip.sound2.envelopeValue);
        //newParam.put("channels.2.inst.1", nothing);
        info.put("channels.3.inst.1",  chip.sound4.envelopeValue);

        // Len
        info.put("channels.0.inst.2",  chip.sound1.length);
        info.put("channels.1.inst.2",  chip.sound2.length);
        //newParam.put("channels.2.inst.2", nothing);
        info.put("channels.3.inst.2",  chip.sound4.length);

        // Duty
        info.put("channels.0.inst.3",  chip.sound1.duty);
        info.put("channels.1.inst.3",  chip.sound2.duty);
        //newParam.put("channels.2.inst.3", nothing);
        //newParam.put("channels.3.inst.3", nothing);

        // Sweep time
        info.put("channels.0.inst.4",  chip.sound1.sweepTime);
        // Sweep shift
        info.put("channels.0.inst.5",  chip.sound1.sweepShift);

        // Len
        info.put("channels.2.inst.4",  chip.sound3.length);
        // Vol
        info.put("channels.2.inst.5",  chip.sound3.level);

        // wf
        for (int i = 0; i < 16; i++) {
            info.put("wf." + i * 2, (byte) ((chip.registers[0x20 + i] >> 4) & 0xf));
            info.put("wf." + i * 2 + 1, (byte) (chip.registers[0x20 + i] & 0xf));
        }

        int r = 10;
        info.put("channels.0.volumeL",  Math.min((chip.sound1.envelopeValue * chip.controller.mode1Left) * 16 / r, 19));
        info.put("channels.0.volumeR",  Math.min((chip.sound1.envelopeValue * chip.controller.mode1Right) * 16 / r, 19));
        info.put("channels.1.volumeL",  Math.min((chip.sound2.envelopeValue * chip.controller.mode2Left) * 16 / r, 19));
        info.put("channels.1.volumeR",  Math.min((chip.sound2.envelopeValue * chip.controller.mode2Right) * 16 / r, 19));
        int lvl = chip.sound3.level == 0 ? 0 : (19 >> (chip.sound3.level - 1));
        info.put("channels.2.volumeL",  Math.min(lvl * chip.controller.mode3Left * 19 / r, 19));
        info.put("channels.2.volumeR",  Math.min(lvl * chip.controller.mode3Right * 19 / r, 19));
        info.put("channels.3.volumeL",  Math.min((chip.sound4.envelopeValue * chip.controller.mode4Left) * 16 / r, 19));
        info.put("channels.3.volumeR",  Math.min((chip.sound4.envelopeValue * chip.controller.mode4Right) * 16 / r, 19));

        return info;
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0xC0, 2d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "info" -> { return getInfo(chipId); }
        }
        return result;
    }
}
