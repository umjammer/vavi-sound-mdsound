package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.QSound;


public class QSoundInst extends Instrument.BaseInstrument {

    private static final int MAX_CHIPS = 0x02;
    private final QSound[] qSoundData = new QSound[] {new QSound(), new QSound()};

    @Override
    public String getName() {
        return "QSoundInst";
    }

    @Override
    public String getShortName() {
        return "QSND";
    }

    @Override
    public void reset(int chipId) {
        QSound chip = qSoundData[chipId];
        chip.reset();

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(int chipId, int samplingRate) {
        if (chipId >= MAX_CHIPS)
            return 0;

        QSound chip = qSoundData[chipId];
        return chip.start(QSound.CLOCK);
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        if (chipId >= MAX_CHIPS)
            return 0;

        QSound chip = qSoundData[chipId];
        return chip.start(clock);
    }

    @Override
    public void stop(int chipId) {
        QSound chip = qSoundData[chipId];
        chip.stop();
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        QSound chip = qSoundData[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    public int qsound_r(int chipId, int offset) {
        QSound chip = qSoundData[chipId];
        return chip.read(offset);
    }

    public void qsound_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        QSound info = qSoundData[chipId];
        info.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void qsound_write_rom(int chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        QSound info = qSoundData[chipId];
        info.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void qsound_set_mute_mask(int chipId, int muteMask) {
        QSound info = qSoundData[chipId];
        info.setMuteMask(muteMask);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        QSound chip = qSoundData[chipId];
        chip.write(adr, data);
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Object> getView(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "Q-Sound");
            case "FAMILY" -> result.put(getName(), "Capcom custom");
            case "VERSION" -> result.put(getName(), "1.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
        }
        return result;
    }
}
