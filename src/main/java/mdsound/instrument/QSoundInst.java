package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.QSound;


public class QSoundInst extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipId) {
        QSound chip = qSoundData[chipId];
        chip.reset();

        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        QSound chip = qSoundData[chipId];
        return chip.start(QSound.CLOCK);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        if (chipId >= MAX_CHIPS)
            return 0;

        QSound chip = qSoundData[chipId];
        return chip.start(clockValue);
    }

    @Override
    public void stop(byte chipId) {
        QSound chip = qSoundData[chipId];
        chip.stop();
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        QSound chip = qSoundData[chipId];
        chip.update(outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private static final int MAX_CHIPS = 0x02;
    private QSound[] qSoundData = new QSound[] {new QSound(), new QSound()};

    @Override
    public String getName() {
        return "QSoundInst";
    }

    @Override
    public String getShortName() {
        return "QSND";
    }

    public void qsound_w(byte chipId, int offset, byte data) {
        QSound chip = qSoundData[chipId];
        chip.write(offset, data);
    }

    public byte qsound_r(byte chipId, int offset) {
        QSound chip = qSoundData[chipId];
        return chip.read(offset);
    }

    public void qsound_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData) {
        QSound info = qSoundData[chipId];
        info.writeRom(romSize, dataStart, dataLength, romData);
    }

    public void qsound_write_rom(byte chipId, int romSize, int dataStart, int dataLength, byte[] romData, int srcStartAddress) {
        QSound info = qSoundData[chipId];
        info.writeRom(romSize, dataStart, dataLength, romData, srcStartAddress);
    }

    public void qsound_set_mute_mask(byte chipId, int muteMask) {
        QSound info = qSoundData[chipId];
        info.setMuteMask(muteMask);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        qsound_w(chipId, adr, (byte) data);
        return 0;
    }

    /**
     * Generic get_info
     */
    /*DEVICE_GET_INFO( QSoundInst ) {
            case DEVINFO_STR_NAME:       strcpy(info.s, "Q-Sound");      break;
            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Capcom custom");    break;
            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
        }
    }*/

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x100, 1d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("qSound", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
