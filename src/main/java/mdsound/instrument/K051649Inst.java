package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.K051649;


public class K051649Inst extends Instrument.BaseInstrument {

    @Override
    public void reset(byte chipId) {
        device_reset_k051649(chipId);
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public int start(byte chipId, int clock) {
        return device_start_k051649(chipId, clock);
    }

    @Override
    public int start(byte chipId, int SamplingRate, int clockValue, Object... Option) {
        if (scc1Data[chipId] == null) {
            scc1Data[chipId] = new K051649();
        }

        int sampRate = device_start_k051649(chipId, clockValue);
        //int flags = 1;
        //if (Option != null && Option.length > 0) flags = (int)(byte)Option[0];
        //k054539_init_flags(chipId, flags);

        return sampRate;
    }

    @Override
    public void stop(byte chipId) {
        device_stop_k051649(chipId);
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        k051649_update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    private static final int MAX_CHIPS = 0x02;
    private K051649[] scc1Data = new K051649[MAX_CHIPS];

    @Override
    public String getName() {
        return "K051649Inst";
    }

    @Override
    public String getShortName() {
        return "K051";
    }

    /* generate Sound to the mix buffer */
    private void k051649_update(byte chipId, int[][] outputs, int samples) {
        K051649 info = scc1Data[chipId];
        info.update(outputs, samples);
    }

    private int device_start_k051649(byte chipId, int clock) {
        if (chipId >= MAX_CHIPS)
            return 0;

        K051649 info = scc1Data[chipId];
        return info.start(clock);
    }

    private void device_stop_k051649(byte chipId) {
        K051649 info = scc1Data[chipId];
    }

    private void device_reset_k051649(byte chipId) {
        K051649 info = scc1Data[chipId];
        info.reset();
    }

    //
    private void k051649_waveform_w(byte chipId, int offset, byte data) {
        K051649 info = scc1Data[chipId];
        info.writeWaveForm(offset, data);
    }

    private byte k051649_waveform_r(byte chipId, int offset) {
        K051649 info = scc1Data[chipId];
        return info.readWaveForm(offset);
    }

    /* SY 20001114: Channel 5 doesn't share the waveform with channel 4 on this chips */
    private void k052539_waveform_w(byte chipId, int offset, byte data) {
        K051649 info = scc1Data[chipId];
        info.writeWaveFormK05239(offset, data);
    }

    private byte k052539_waveform_r(byte chipId, int offset) {
        K051649 info = scc1Data[chipId];
        return info.readWaveFormK05239(offset);
    }

    private void k051649_volume_w(byte chipId, int offset, byte data) {
        K051649 info = scc1Data[chipId];
        info.writeVolume(offset, data);
    }

    private void k051649_frequency_w(byte chipId, int offset, byte data) {
        K051649 info = scc1Data[chipId];
        info.writeFrequency(offset, data);
    }

    private void k051649_keyonoff_w(byte chipId, int offset, byte data) {
        K051649 info = scc1Data[chipId];
        info.wtiteKeyOnOff(offset, data);
    }

    private void k051649_test_w(byte chipId, int offset, byte data) {
        K051649 info = scc1Data[chipId];
        info.writeTest(offset, data);
    }

    private byte k051649_test_r(byte chipId, int offset) {
        // reading the test register sets it to $ff!
        k051649_test_w(chipId, offset, (byte) 0xff);
        return (byte) 0xff;
    }

    private void k051649_w(byte chipId, int offset, byte data) {
        K051649 info = scc1Data[chipId];
        info.write(offset, data);
    }

    private void k051649_set_mute_mask(byte chipId, int muteMask) {
        K051649 info = scc1Data[chipId];
        info.setMuteMask(muteMask);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        k051649_w(chipId, adr, (byte) data);
        return 0;
    }

    public K051649 GetK051649_State(byte chipId) {
        return scc1Data[chipId];
    }

    /**
     * Generic get_info
     */
    /*DEVICE_GET_INFO( k051649 ) {
            case DEVINFO_STR_NAME:       strcpy(info.s, "K051649Inst");      break;
            case DEVINFO_STR_FAMILY:     strcpy(info.s, "Konami custom");    break;
            case DEVINFO_STR_VERSION:     strcpy(info.s, "1.0");       break;
            case DEVINFO_STR_CREDITS:     strcpy(info.s, "Copyright Nicola Salmoria and the MAME Team"); break;
        }
    }*/

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0xA0, 1d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("k051649", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
