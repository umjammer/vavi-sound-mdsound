package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import dotnet4j.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.chips.PcmChip;


// RF5C164
public class ScdPcmInst extends Instrument.BaseInstrument {

    //private static final int MAX_CHIPS = 0x02;
    public PcmChip[] PCM_Chip = new PcmChip[] {new PcmChip(), new PcmChip()};

    private int PCM_Init(byte chipId, int rate) {
        PcmChip chip = PCM_Chip[chipId];
        return chip.init(rate);
    }

    private void PCM_Reset(byte chipId) {
        PcmChip chip = PCM_Chip[chipId];
        chip.reset();
    }

    private void PCM_Set_Rate(byte chipId, int rate) {
        PcmChip chip = PCM_Chip[chipId];
        chip.setRate(rate);
    }

    private void PCM_Write_Reg(byte chipId, int reg, int data) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeReg(reg, data);
    }

    private int PCM_Update(byte chipId, int[][] buf, int length) {
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
        //0..Main
        visVolume = new int[][][] {
                new int[][] {new int[] {0, 0}},
                new int[][] {new int[] {0, 0}}
        };
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        PcmChip chip = PCM_Chip[chipId];

        PCM_Update(chipId, outputs, samples);

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    // samplingRate 未使用
    @Override
    public int start(byte chipId, int samplingRate, int clockValue, Object... option) {
        return start(chipId, clockValue);
    }

    @Override
    public int start(byte chipId, int clock) {
        if (chipId >= 0x02)
            return 0;

        int rate = (clock & 0x7FFFFFFF) / 384;
        if (((CHIP_SAMPLING_MODE & 0x01) != 0 && rate < CHIP_SAMPLE_RATE) ||
                CHIP_SAMPLING_MODE == 0x02)
            rate = CHIP_SAMPLE_RATE;
        PCM_Init(chipId, rate);

        PcmChip chip = PCM_Chip[chipId];
        chip.start(clock);
        return rate;
    }

    @Override
    public void stop(byte chipId) {
        PcmChip chip = PCM_Chip[chipId];
    }

    @Override
    public void reset(byte chipId) {
        PCM_Reset(chipId);
    }

    private void rf5c164_w(byte chipId, int offset, byte data) {
        PCM_Write_Reg(chipId, offset, data);
    }

    public void rf5c164_mem_w(byte chipId, int offset, byte data) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeMem(offset, data);
    }

    public void rf5c164_write_ram(byte chipId, int dataStart, int dataLength, byte[] ramData) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeRam(dataStart, dataLength, ramData);
    }

    public void rf5c164_write_ram2(byte chipId, int RAMStartAdr, int RAMdataLength, byte[] SrcData, int SrcStartAdr) {
        PcmChip chip = PCM_Chip[chipId];
        chip.writeRam2(RAMStartAdr, RAMdataLength, SrcData, SrcStartAdr);
    }

    public void rf5c164_set_mute_mask(byte chipId, int muteMask) {
        PcmChip chip = PCM_Chip[chipId];
        chip.setMuteMask(muteMask);
    }

    public void rf5c164_set_mute_Ch(byte chipId, int ch, int mute) {
        PcmChip chip = PCM_Chip[chipId];
        chip.setMuteCh(ch, mute);
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        rf5c164_w(chipId, adr, (byte) data);
        return 0;
    }

    //----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x80, 2d);
    }

    @Override
    public Map<String, Integer> getVisVolume() {
        Map<String, Integer> result = new HashMap<>();
        result.put("rf5c164", getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
        return result;
    }
}
