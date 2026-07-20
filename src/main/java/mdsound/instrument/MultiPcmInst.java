package mdsound.instrument;

import java.util.HashMap;
import java.util.Map;

import vavi.util.compat.Tuple;
import mdsound.Instrument;
import mdsound.Instrument.PcmEnabledInstrument;
import mdsound.chips.MultiPCM;


// TODO check SHIFT in all classes
public class MultiPcmInst extends Instrument.BaseInstrument implements PcmEnabledInstrument {

    public static final int CHANNELS = 28;

    public static final int MAX_CHIPS = 0x02;

    private final MultiPCM[] chips = {new MultiPCM(), new MultiPCM()};

    public MultiPcmInst() {
        visVolume = new int[][][] {{{0, 0}}, {{0, 0}}};
    }

    @Override
    public String getName() {
        return "Multi PCM";
    }

    @Override
    public String getShortName() {
        return "mPCM";
    }

    @Override
    public void reset(int chipId) {
        chips[chipId].reset();
    }

    @Override
    public int start(int chipId, int samplingRate, int clock, Object... option) {
        assert chipId < MAX_CHIPS;
        return chips[chipId].start(clock);
    }

    @Override
    public int read(int chipId, int adr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(int chipId, int port, int adr, int data) {
        chips[chipId].write(adr, data);
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
        chips[chipId].stop();
    }

    /** the channels muted on each chip, as a bit a channel */
    private final int[] mask = {0, 0};

    @Override
    public synchronized void setMask(int chipId, int ch) {
        mask[chipId] |= ch;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    @Override
    public synchronized void resetMask(int chipId, int ch) {
        mask[chipId] &= ~ch;
        chips[chipId].setMuteMask(mask[chipId]);
    }

    // ----

    public synchronized void writeBank(int chipId, int ch, int adr) {
        int bankMask = ch & 0x03;
        if (bankMask == 0x03 && (adr & 0x08) == 0) {
            // 1 MB banking (reg 0x10)
            chips[chipId].write(0x10, adr / 0x10);
        } else {
            // 512 KB banking (regs 0x11/0x12)
            if ((bankMask & 0x02) != 0) // low bank
                chips[chipId].write(0x11, adr / 0x08);
            if ((bankMask & 0x01) != 0) // high bank
                chips[chipId].write(0x12, adr / 0x08);
        }
    }

    /** @param extras 0: srcOffset, 1: romSize */
    @Override
    public synchronized void writePcm(int chipId, byte[] buf, int offset, int length, Object... extras) {
        int srcOffset = (int) extras[0];
        int romSize = (int) extras[1];
        chips[chipId].writeRom(romSize, offset, length, buf, srcOffset);
    }

    private synchronized Map<String, Object> getInfo(int chipId) {
        MultiPCM chip = chips[chipId];

        Map<String, Object> info = new HashMap<>();
        for (int ch = 0; ch < 28; ch++) {
            int oct = ((chip.getSlot(ch).regs[3] >> 4) - 1) & 0xf;
            oct = ((oct & 0x8) != 0) ? (oct - 16) : oct;
            oct = oct + 4; // The fundamental tone is o5.
            int pitch = ((chip.getSlot(ch).regs[3] & 0xf) << 6) | (chip.getSlot(ch).regs[2] >> 2);

            int nt = Math.clamp(oct * 12 + pitch / 85, 0, 7 * 12);
            info.put("channels." + ch + ".note",  nt);

            int d = chip.getSlot(ch).pan;
            d = (d == 0) ? 0xf : d;
            info.put("channels." + ch + ".pan", ((((d & 0xc) >> 2) * 4) << 4) | (((d & 0x3) * 4) << 0));
            // the panpot as written: 0 centre, 8 silent, 1-7 leftwards, 9-15 rightwards
            info.put("channels." + ch + ".panpot", chip.getSlot(ch).pan);

            info.put("channels." + ch + ".bit.0", (chip.getSlot(ch).regs[4] & 0x80) != 0);
            // the key on bit above is what was asked for; these are what the slot is actually doing
            info.put("channels." + ch + ".playing", chip.getSlot(ch).isPlaying());
            info.put("channels." + ch + ".mute", chip.getSlot(ch).isMuted());
            info.put("channels." + ch + ".totalLevel", chip.getSlot(ch).getTotalLevel());
            info.put("channels." + ch + ".freq", ((chip.getSlot(ch).regs[3] & 0xf) << 6) | (chip.getSlot(ch).regs[2] >> 2));
            info.put("channels." + ch + ".bit.1", (chip.getSlot(ch).regs[5] & 1) != 0); // TL Interpolation
            info.put("channels." + ch + ".inst.1", (chip.getSlot(ch).regs[5] >> 1) & 0x7f); // TL
            info.put("channels." + ch + ".inst.2", (chip.getSlot(ch).regs[6] >> 3) & 7); // LFO freq
            info.put("channels." + ch + ".inst.3", (chip.getSlot(ch).regs[6]) & 7); // PLFO
            info.put("channels." + ch + ".inst.4", (chip.getSlot(ch).regs[7]) & 7); // ALFO

            if (chip.getSlot(ch).sample != null) {
                info.put("channels." + ch + ".inst.0", chip.getSlot(ch).regs[1]);
                info.put("channels." + ch + ".sadr", chip.getSlot(ch).sample.start);
                info.put("channels." + ch + ".eadr", chip.getSlot(ch).sample.end);
                info.put("channels." + ch + ".ladr", chip.getSlot(ch).sample.loop);
                info.put("channels." + ch + ".inst.5", chip.getSlot(ch).sample.lfoVib);
                info.put("channels." + ch + ".inst.6", chip.getSlot(ch).sample.ar);
                info.put("channels." + ch + ".inst.7", chip.getSlot(ch).sample.dr1);
                info.put("channels." + ch + ".inst.8", chip.getSlot(ch).sample.dr2);
                info.put("channels." + ch + ".inst.9", chip.getSlot(ch).sample.dl);
                info.put("channels." + ch + ".inst.10", chip.getSlot(ch).sample.rr);
                info.put("channels." + ch + ".inst.11", chip.getSlot(ch).sample.krs);
                info.put("channels." + ch + ".inst.12", chip.getSlot(ch).sample.am);
            }
        }

        return info;
    }

    // ----

    @Override
    public Tuple<Integer, Double> getRegulationVolume() {
        return new Tuple<>(0x40, 4d);
    }

    @Override
    public Map<String, Object> getView(int chipId, String key, Object... args) {
        Map<String, Object> result = new HashMap<>();
        switch (key) {
            case "volume" ->
                    result.put(getName(), getMonoVolume(visVolume[0][0][0], visVolume[0][0][1], visVolume[1][0][0], visVolume[1][0][1]));
            case "NAME" -> result.put(getName(), "Sega/Yamaha 315-5560");
            case "FAMILY" -> result.put(getName(), "Sega custom");
            case "VERSION" -> result.put(getName(), "2.0");
            case "CREDITS" -> result.put(getName(), "Copyright Nicola Salmoria and the MAME Team");
            case "info" -> result.putAll(getInfo(chipId));
        }
        return result;
    }
}
