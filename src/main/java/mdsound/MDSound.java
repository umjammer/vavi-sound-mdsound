/*
 * https://github.com/kuma4649/MDSound
 */

package mdsound;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dotnet4j.util.compat.QuadConsumer;
import dotnet4j.util.compat.TriConsumer;
import mdsound.instrument.NesInst;

import static java.lang.System.getLogger;


public class MDSound {

    private static final Logger logger = getLogger(MDSound.class.getName());

    private final Resampler resampler = new Resampler();

    public DacControl dacControl = null;

    private List<Chip> chips = null;

    public MDSound.Chip getChipInfo(Class<? extends Instrument> inst) {
        return chips.stream().filter(c -> c.instrument.getClass() == inst).findFirst().orElse(null);
    }

    private final Map<Class<? extends Instrument>, List<Instrument>> instruments = new HashMap<>();

    /** @return nullable */
    public <T extends Instrument> T inst(Class<T> clazz) {
        return inst(clazz, 0);
    }

    /** @return nullable */
    public <T extends Instrument> T inst(Class<T> clazz, int chipIndex) {
        if (instruments.containsKey(clazz) && chipIndex < instruments.get(clazz).size()) {
            return clazz.cast(instruments.get(clazz).get(chipIndex));
        } else {
            return null;
        }
    }

    private final int[][] buffer = new int[][] {new int[1], new int[1]};

    private boolean incFlag = false;
    private double volumeMul;

    /** view */
    public VisWaveBuffer visWaveBuffer = new VisWaveBuffer();

    /** */
    public static class Chip {

        public interface AdditionalUpdate extends QuadConsumer<Chip, Integer, int[][], Integer> {
        }
        public interface SetVolume extends TriConsumer<String, Integer, Double> {
        }

        public Instrument instrument = null;
        public AdditionalUpdate additionalUpdate = null;
        public Map<String, SetVolume> setVolumes = new HashMap<>();

        public static final String MAIN_TAG = "MAIN";

        {
            setVolumes.put(MAIN_TAG, this::setDefaultVolume);
        }

        public int id = 0;
        public int samplingRate = 0;
        public int clock = 0;
        public int volume = 0;
        public int visVolume = 0;

        public int resampler;
        public int smpP;
        public int smpLast;
        public int smpNext;
        public int[] lSmpl;
        public int[] nSmpl;

        public Object[] option = null;

        int tVolume;

        public int getTVolume() {
            return tVolume;
        }

        int volumeBalance = 0x100;

        public int getVolumeBalance() {
            return volumeBalance;
        }

        int tVolumeBalance;

        public int getTVolumeBalance() {
            return tVolumeBalance;
        }

        public void setVolume(String tag, int vol, double volumeMul) {
            SetVolume setVolume = setVolumes.get(tag);
            if (setVolume != null)
                setVolumes.get(tag).accept(tag, vol, volumeMul);
            else
                // add "chip.setVolumes.put(TAG, method_reference::for_volume)" in your plugin
                logger.log(Level.WARNING, "no such tag: " + tag, new Exception("no such tag: " + tag + ", instrument: " + instrument.getClass().getName()));
        }

        // TODO used only nes
        public SetVolume mainWrappedSetVolume(SetVolume setVolume) {
            return (t, i, d) -> {
                setDefaultVolume(t, i, d);
                setVolume.accept(t, i, d);
            };
        }

        private void setDefaultVolume(String tag, int vol, double volumeMul) {
            this.volume = Math.max(Math.min(vol, 20), -192);
            int n = (((int) (16384.0 * Math.pow(10.0, this.volume / 40.0)) * this.tVolumeBalance) >> 8);
            this.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }

        // default, 0x80, 1
        // UPD7759, 0x11E, 1
        // SCSP, 0x20, 8
        // VSU, 0x100, 1
        // ES5503, 0x40, 8
        // ES5506, 0x20, 16
        private int getRegulationVolume(double[] mul) {
            var r = instrument.getRegulationVolume();
            mul[0] = r.getItem2();
            return r.getItem1();
        }

        // TODO naming
        public int volume1(double[] mul, int size) {
            if (this.instrument instanceof NesInst) this.volume = 0; // TODO nes tight coupled
            int balance = this.getRegulationVolume(mul);
            //16384 = 0x4000 = short.MAXValue + 1
            return (int) ((((int) (16384.0 * Math.pow(10.0, 0 / 40.0)) * balance) >> 8) * mul[0]) / size;
        }

        // TODO naming
        public void volume2(double[] mul, double volumeMul) {
            if ((this.volumeBalance & 0x8000) != 0)
                this.tVolumeBalance = (this.getRegulationVolume(mul) * (this.volumeBalance & 0x7fff) + 0x80) >> 8;
            else
                this.tVolumeBalance = this.volumeBalance;
            int n = (((int) (16384.0 * Math.pow(10.0, this.volume / 40.0)) * this.tVolumeBalance) >> 8);
            this.tVolume = Math.max(Math.min((int) (n * volumeMul), Short.MAX_VALUE), Short.MIN_VALUE);
        }
    }

    /** */
    public MDSound() {
        this(Resampler.DefaultSamplingRate, Resampler.DefaultSamplingBuffer, null);
    }

    /** */
    public MDSound(int samplingRate, int samplingBuffer, List<Chip> insts) {
        init(samplingRate, samplingBuffer, insts);
    }

    /** */
    public synchronized void init(int samplingRate, int samplingBuffer, List<Chip> chips) {
        if (chips == null) {
logger.log(Level.WARNING, "no chips");
            return;
        }

        this.chips = chips;
        incFlag = false;

        resampler.init(chips, samplingRate, samplingBuffer);

        instruments.clear();

        // Calculate the actual multiple from the volume value
        int total = 0;
        double[] mul = new double[1];
        for (Chip chip : chips) {
            total += chip.volume1(mul, chips.size());
        }
        // Calculate the multiple from the total volume value to the maximum volume
        volumeMul = 16384.0 / total;
        // Calculate the actual multiple from the volume value
        for (Chip chip : chips) {
            chip.volume2(mul, volumeMul);
        }

        for (Chip chip : chips) {
logger.log(Level.DEBUG, "instrument start/reset: %s[%d], %d, %d".formatted(chip.instrument.getClass().getSimpleName(), chip.id, chip.samplingRate, chip.clock));
            chip.samplingRate = chip.instrument.start(chip.id, chip.samplingRate, chip.clock, chip.option);
            chip.instrument.reset(chip.id);

            if (instruments.containsKey(chip.instrument.getClass())) {
                instruments.get(chip.instrument.getClass()).add(chip.instrument);
            } else {
                instruments.put(chip.instrument.getClass(), new ArrayList<>(List.of(chip.instrument)));
            }

            resampler.setup(chip);
        }
instruments.keySet().forEach(k -> logger.log(Level.DEBUG, "instrument: " + k.getSimpleName().replace("Inst", "") + ": chips: " + instruments.get(k).stream().map(Instrument::getName).collect(Collectors.joining(", ", "[", "]"))));

        dacControl = new DacControl(samplingRate, this);

        instruments.values().forEach(is -> is.forEach(Instrument::init));
    }

    /** */
    public synchronized int update(short[] buf, int offset, int sampleCount, Runnable frame) {
        int i;
        for (i = 0; i < sampleCount && offset + i < buf.length; i += 2) {

            if (frame != null) frame.run();

            if (dacControl != null) dacControl.update();

            int[] a = new int[1];
            int[] b = new int[1];

            buffer[0][0] = 0;
            buffer[1][0] = 0;
            resampler.resample(buffer, 1);
//if (buffer[0][0] != 0) logger.log(Level.DEBUG, "%d".formatted(buffer[0][0]));
            a[0] += buffer[0][0];
            b[0] += buffer[1][0];

            if (incFlag) {
                a[0] += buf[offset + i + 0];
                b[0] += buf[offset + i + 1];
            }

            clip(a, b);

            buf[offset + i + 0] = (short) a[0];
            buf[offset + i + 1] = (short) b[0];
logger.log(Level.TRACE, "[%d] %+04d, %+04d".formatted(i, a[0], b[0]));
            visWaveBuffer.enq((short) a[0], (short) b[0]);
        }

        return Math.min(i, sampleCount);
    }

    private static void clip(int[] a, int[] b) {
        if (((a[0] + 32767) & 0xffff) > 32767 * 2) {
            if ((a[0] + 32767) >= (32767 * 2)) {
                a[0] = 32767;
            } else {
                a[0] = -32767;
            }
        }
        if (((b[0] + 32767) & 0xffff) > 32767 * 2) {
            if ((b[0] + 32767) >= (32767 * 2)) {
                b[0] = 32767;
            } else {
                b[0] = -32767;
            }
        }
    }

    public void write(Class<? extends Instrument> i, int chipId, int port, int adr, int data) {
        write(i, 0, chipId, port, adr, data);
    }

    public synchronized void write(Class<? extends Instrument> i, int chipIndex, int chipId, int port, int adr, int data) {
        if (!instruments.containsKey(i)) {
logger.log(Level.WARNING, "not contains: " + i);
            return;
        }

//logger.log(Level.TRACE, "mds: %02x".formatted(data)); // ok
        instruments.get(i).get(chipIndex).write(chipId, port, adr, data);
    }

//#region SetVolume

    public void setVolume(String tag, Class<? extends Instrument> i, int vol) {
        if (!instruments.containsKey(i)) return;

        if (chips == null) return;

        for (Chip c : chips) {
            if (c.instrument.getClass() != i) continue;
            c.setVolume(tag, vol, volumeMul);
        }
    }

//#endregion

//#region VisVolume

    public Set<Instrument> getFirstInstruments() {
        return instruments.values().stream().map(is -> is.get(0)).collect(Collectors.toSet());
    }

    /**
     * Gets the whole volume of the Left. (for visual effects)
     */
    public synchronized int getTotalVolumeL() {
        int v = 0;
        for (int i = 0; i < buffer[0].length; i++) {
            v = Math.max(v, Math.abs(buffer[0][i]));
        }
        return v;
    }

    /**
     * Gets whole volume of the Right. (for visual effects)
     */
    public synchronized int getTotalVolumeR() {
        int v = 0;
        for (int i = 0; i < buffer[1].length; i++) {
            v = Math.max(v, Math.abs(buffer[1][i]));
        }
        return v;
    }

//#endregion

    public synchronized void setIncFlag() {
        incFlag = true;
    }

    public synchronized void resetIncFlag() {
        incFlag = false;
    }
}
