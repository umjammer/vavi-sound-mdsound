package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.PcmChip;


// RF5C164
public class ScdPcmInst extends Instrument.BaseInstrument {

//    private static final int MAX_CHIPS = 0x02;
    public PcmChip[] PCM_Chip = new PcmChip[] {new PcmChip(), new PcmChip()};

    private int PCM_Init(int chipId, int rate) {
        PcmChip chip = PCM_Chip[chipId];
        return chip.init(rate);
    }

    private void PCM_Reset(int chipId) {
        PcmChip chip = PCM_Chip[chipId];
        chip.reset();
    }

    private void PCM_Set_Rate(int chipId, int rate) {
        PcmChip chip = PCM_Chip[chipId];
        chip.setRate(rate);
    }

    private void PCM_Write_Reg(int chipId, int reg, int data) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeReg(reg, data);
    }

    private int PCM_Update(int chipId, int[][] buf, int length) {
        PcmChip chip = PCM_Chip[chipId];
        return chip.update(buf, length);
    }

    @Override
    public String getName() {
        return "RF5C164";
    }

    @Override
    public String getShortName() {
        return "RF5C";
    }

    public ScdPcmInst() {
        // 0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void update(int chipId, int[][] outputs, int samples) {
        PcmChip chip = PCM_Chip[chipId];

        PCM_Update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    // samplingRate 未使用
    @Override
    public int start(int chipId, int samplingRate, int clockValue, Object... option) {
        return start(chipId, clockValue);
    }

    @Override
    public int start(int chipId, int clock) {
        if (chipId >= 0x02)
            return 0;

        int rate = (clock & 0x7fff_ffff) / 384;
        if (((CHIP_SAMPLING_MODE & 0x01) != 0 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;
        PCM_Init(chipId, rate);

        PcmChip chip = PCM_Chip[chipId];
        chip.start(clock);
        return rate;
    }

    @Override
    public void stop(int chipId) {
        PcmChip chip = PCM_Chip[chipId];
    }

    @Override
    public void reset(int chipId) {
        PCM_Reset(chipId);
    }

    private void rf5c164_w(int chipId, int offset, int data) {
        PCM_Write_Reg(chipId, offset, data);
    }

    public void rf5c164_mem_w(int chipId, int offset, int data) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeMem(offset, data);
    }

    public void rf5c164_write_ram(int chipId, int dataStart, int dataLength, byte[] ramData) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeRam(dataStart, dataLength, ramData);
    }

    public void rf5c164_write_ram2(int chipId, int ramStartAdr, int ramDataLength, byte[] srcData, int srcStartAdr) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeRam2(ramStartAdr, ramDataLength, srcData, srcStartAdr);
    }

    public void rf5c164_set_mute_mask(int chipId, int muteMask) {
        PcmChip chip = PCM_Chip[chipId];
        chip.setMuteMask(muteMask);
    }

    public void rf5c164_set_mute_Ch(int chipId, int ch, int mute) {
        PcmChip chip = PCM_Chip[chipId];
        chip.setMuteCh(ch, mute);
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        rf5c164_w(chipId, adr, data);
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x80, 2d);
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
