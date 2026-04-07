/*
 * license:BSD-3-Clause
 *
 * copyright-holders: R. Belmont
 */

package mdsound.chips;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;


/**
  ES5503 - Ensoniq ES5503 "DOC" emulator v2.1.2
  By R. Belmont.
  Copyright R. Belmont.
  <pre>
  History: the ES5503 was the next design after the famous C64 "SID" by Bob Yannes.
  It powered the legendary Mirage sampler (the first affordable pro sampler) as well
  as the ESQ-1 synth/sequencer.  The ES5505 (used in Taito's F3 System) and 5506
  (used in the "Soundscape" series of ISA PC sound cards) followed on a fundamentally
  similar architecture.
  Bugs: On the real silicon, oscillators 30 and 31 have random volume fluctuations and are
  unusable for playback.  We don't attempt to emulate that. :-)
  Additionally, in "swap" mode, there's one cycle when the switch takes place where the
  oscillator's output is 0x80 (centerline) regardless of the sample data.  This can
  cause audible clicks and a general degradation of audio quality if the correct sample
  data at that point isn't 0x80 or very near it.
  Changes:
  0.2 (RB) - improved behavior for volumes > 127, fixes missing notes in Nucleus & missing voices in Thexder
  0.3 (RB) - fixed extraneous clicking, improved timing behavior for e.g. Music Construction Set & Music Studio
  0.4 (RB) - major fixes to IRQ semantics and end-of-sample handling.
  0.5 (RB) - more flexible wave memory hookup (incl. banking) and save state support.
  1.0 (RB) - properly respects the input clock
  2.0 (RB) - C++ conversion, more accurate oscillator IRQ timing
  2.1 (RB) - Corrected phase when looping; synthLAB, Arkanoid, and Arkanoid II no longer go out of tune
  2.1.1 (RB) - Fixed issue introduced in 2.0 where IRQs were delayed
  2.1.2 (RB) - Fixed SoundSmith POLY.SYNTH inst where one-shot on the even oscillator and swap on the odd should loop.
               Conversely, the intro voice in FTA Delta Demo has swap on the even and one-shot on the odd and doesn't
               want to loop.
 * </pre>
 *
 * @author R. Belmont
 */
public class Es5503 {

    private enum MODE {
        FREE,
        ONESHOT,
        SYNCAM,
        SWAP
    }

    private static class ES5503Osc {

        private int freq;
        private int wtSize;
        private int control;
        private int vol;
        private int data;
        private int waveTblPointer;
        private int waveTblSize;
        private int resolution;

        private int accumulator;
        private int irqPend;

        private int muted;
    }

    //private DEV_DATA _devData;

    private int dramSize;
    private byte[] docRam;

    private BiConsumer<Object, Integer> irq_func; // IRQ callback
    private Object irq_param;

    private BiFunction<Object, Integer, Integer> adc_func; // callback for the 5503's built-in analog to digital converter
    private Object adc_param;

    private final ES5503Osc[] oscillators = {
            new ES5503Osc(), new ES5503Osc(), new ES5503Osc(), new ES5503Osc(),
            new ES5503Osc(), new ES5503Osc(), new ES5503Osc(), new ES5503Osc(),
            new ES5503Osc(), new ES5503Osc(), new ES5503Osc(), new ES5503Osc(),
            new ES5503Osc(), new ES5503Osc(), new ES5503Osc(), new ES5503Osc(),
            new ES5503Osc(), new ES5503Osc(), new ES5503Osc(), new ES5503Osc(),
            new ES5503Osc(), new ES5503Osc(), new ES5503Osc(), new ES5503Osc(),
            new ES5503Osc(), new ES5503Osc(), new ES5503Osc(), new ES5503Osc(),
            new ES5503Osc(), new ES5503Osc(), new ES5503Osc(), new ES5503Osc()
    };

    private int oscsEnabled;      // # of oscillators enabled
    private int regE0;            // contents of register 0xe0

    private int channel_strobe;

    private int clock;
    private int output_channels;
    private int outChn_mask;
    private int output_rate;

    private Consumer<Integer> smpRateFunc = null;

    // useful constants
    private static final int[] waveSizes = {256, 512, 1024, 2048, 4096, 8192, 16384, 32768};
    private static final int[] waveMasks = {0x1ff00, 0x1fe00, 0x1fc00, 0x1f800, 0x1f000, 0x1e000, 0x1c000, 0x18000};
    private static final int[] accMasks = {0xff, 0x1ff, 0x3ff, 0x7ff, 0xfff, 0x1fff, 0x3fff, 0x7fff};
    private static final int[] resShifts = {9, 10, 11, 12, 13, 14, 15, 16};

    // halt_osc: handle halting an oscillator
    // chip = chip ptr
    // oNum = oscillator #
    // type = 1 for 0 found in sample data, 0 for hit end of table size
    private void es5503_halt_osc(int oNum, int type, /* ref */ int[] accumulator, int resShift) {
        ES5503Osc pOsc = this.oscillators[oNum];
        ES5503Osc pPartner = this.oscillators[oNum ^ 1];
        int mode = (pOsc.control >> 1) & 3;
        int partnerMode = (pPartner.control >> 1) & 3;

        // if 0 found in sample data or mode is not free-run, halt this oscillator
        if ((mode != MODE.FREE.ordinal()) || (type != 0)) {
            pOsc.control |= 1;
        } else { // preserve the relative phase of the oscillator when looping
            int wtSize = pOsc.wtSize - 1;
            int altram = accumulator[0] >> resShift;

            if (altram > wtSize) {
                altram -= wtSize;
            } else {
                altram = 0;
            }

            accumulator[0] = altram << resShift;
        }

        // if we're in swap mode or we're the even oscillator and the partner is in swap mode,
        // start the partner.
        if ((mode == MODE.SWAP.ordinal()) || ((partnerMode == MODE.SWAP.ordinal()) && ((oNum & 1) == 0))) {
            pPartner.control &= 0xff; // ~1; // clear the halt bit
            pPartner.accumulator = 0; // and make sure it starts from the top (does this also need phase preservation?)
        }

        // IRQ enabled for this voice?
        if ((pOsc.control & 0x08) != 0) {
            pOsc.irqPend = 1;

            if (this.irq_func != null)
                this.irq_func.accept(this.irq_param, 1);
        }
    }

    public void update(int samples, int[][] outputs) {
        int osc;
        int sNum;
        int ramPtr;
        int chnsStereo, chan;

        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;
        }
        if (this.docRam == null)
            return;

        chnsStereo = this.output_channels & 0xfe; // ~1;
        for (osc = 0; osc < this.oscsEnabled; osc++) {
            ES5503Osc pOsc = this.oscillators[osc];

            if ((pOsc.control & 1) == 0 && pOsc.muted == 0) {
                int wtPtr = pOsc.waveTblPointer & waveMasks[pOsc.waveTblSize];
                int altRam;
                int[] acc = new int[] {pOsc.accumulator};
                int wtSize = pOsc.wtSize - 1;
                int freq = pOsc.freq;
                int vol = pOsc.vol;
                int chnMask = (pOsc.control >> 4) & 0x0f;
                int resShift = resShifts[pOsc.resolution] - pOsc.waveTblSize;
                int sizeMask = accMasks[pOsc.waveTblSize];
                int outData;

                chnMask &= this.outChn_mask;
                for (sNum = 0; sNum < samples; sNum++) {
                    altRam = acc[0] >> resShift;
                    ramPtr = altRam & sizeMask;

                    acc[0] += freq;

                    // channel strobe is always valid when reading; this allows potentially banking per voice
                    this.channel_strobe = (pOsc.control >> 4) & 0xf;
                    pOsc.data = this.docRam[ramPtr + wtPtr] & 0xff;

                    if (pOsc.data == 0x00) {
                        es5503_halt_osc(osc, 1, /* ref */ acc, resShift);
                    } else {
                        outData = (pOsc.data - 0x80) * vol;

                        // send groups of 2 channels to L or R
                        for (chan = 0; chan < chnsStereo; chan++) {
                            if (chan == chnMask)
                                outputs[chan & 1][sNum] += outData;
                        }
                        outData = (outData * 181) >> 10; // 8; // outData *= sqrt(2)
                        // send remaining channels to L+R
                        for (; chan < this.output_channels; chan++) {
                            if (chan == chnMask) {
                                outputs[0][sNum] += outData;
                                outputs[1][sNum] += outData;
                            }
                        }

                        if (altRam >= wtSize) {
                            es5503_halt_osc(osc, 0, /* ref */ acc, resShift);
                        }
                    }

                    // if oscillator halted, we've got no more samples to generate
                    if ((pOsc.control & 1) != 0) {
                        pOsc.control |= 1;
                        break;
                    }
                }

                pOsc.accumulator = acc[0];
            }
        }
    }

    public int start(int clock, int flags) {

        this.irq_func = null;
        this.irq_param = null;
        this.adc_func = null;
        this.adc_param = null;

        this.dramSize = 0x2_0000; // 128 KB
        this.docRam = new byte[this.dramSize];
        this.clock = clock; // cfg.clock;
        this.output_channels = flags; // cfg.flags;
        if (this.output_channels == 0)
            this.output_channels = 1;
        this.outChn_mask = pow2_mask(this.output_channels);

        this.oscsEnabled = 1;
        this.output_rate = (this.clock / 8) / 34; // (2 + this.oscsEnabled)); // (input clock / 8) / # of oscs. enabled + 2

        setMuteMask(0x0000_0000);

        //this._devData.chipInf = chip;
        //INIT_DEVINF(retDevInf, &this._devData, this.output_rate, &devDef);

        return 0x00;
    }

    private static int pow2_mask(int v) {
        if (v == 0)
            return 0;
        v--;
        v |= (v >> 1);
        v |= (v >> 2);
        v |= (v >> 4);
        v |= (v >> 8);
        v |= (v >> 16);
        return v;
    }

    public void stop() {
    }

    public void reset() {
        this.regE0 = 0xff;

        for (int osc = 0; osc < 32; osc++) {
            ES5503Osc tempOsc = this.oscillators[osc];
            tempOsc.freq = 0;
            tempOsc.wtSize = 0;
            tempOsc.control = 0;
            tempOsc.vol = 0;
            tempOsc.data = 0x80;
            tempOsc.waveTblPointer = 0;
            tempOsc.waveTblSize = 0;
            tempOsc.resolution = 0;
            tempOsc.accumulator = 0;
            tempOsc.irqPend = 0;
        }

        this.oscsEnabled = 1;

        this.channel_strobe = 0;
        for (int i = 0; i < this.dramSize; i++) this.docRam[i] = 0x00;

        this.output_rate = (this.clock / 8) / (2 + this.oscsEnabled);  // (input clock / 8) / # of oscs. enabled + 2
        if (this.smpRateFunc != null)
            this.smpRateFunc.accept(this.output_rate);
    }

    public int read(int offset) {
        int retval;

        if (offset < 0xe0) {
            int osc = offset & 0x1f;

            switch (offset & 0xe0) {
                case 0: // freq lo
                    return this.oscillators[osc].freq & 0xff;

                case 0x20: // freq hi
                    return (this.oscillators[osc].freq & 0xff00) >> 8;

                case 0x40: // volume
                    return this.oscillators[osc].vol;

                case 0x60: // data
                    return this.oscillators[osc].data;

                case 0x80: // wave table pointer
                    return (this.oscillators[osc].waveTblPointer >> 8) & 0xff;

                case 0xa0: // oscillator control
                    return this.oscillators[osc].control;

                case 0xc0: // bank select / wavetable size / resolution
                    retval = 0;
                    if ((this.oscillators[osc].waveTblPointer & 0x10000) != 0) {
                        retval |= 0x40;
                    }

                    retval |= this.oscillators[osc].waveTblSize << 3;
                    retval |= this.oscillators[osc].resolution;
                    return retval;
            }
        } else { // global registers
            switch (offset) {
                case 0xe0: // interrupt status
                    retval = this.regE0;

                    if (this.irq_func != null)
                        this.irq_func.accept(this.irq_param, 0);

                    // scan all oscillators
                    for (int i = 0; i < this.oscsEnabled; i++) {
                        if (this.oscillators[i].irqPend != 0) {
                            // signal this oscillator has an interrupt
                            retval = (i << 1) & 0xff;

                            this.regE0 = retval | 0x80;

                            // and clear its flag
                            this.oscillators[i].irqPend = 0;
                            break;
                        }
                    }

                    // if any oscillators still need to be serviced, assert IRQ again immediately
                    for (int i = 0; i < this.oscsEnabled; i++) {
                        if (this.oscillators[i].irqPend != 0) {
                            if (this.irq_func != null)
                                this.irq_func.accept(this.irq_param, 1);
                            break;
                        }
                    }

                    return retval | 0x41;

                case 0xe1:  // oscillator enable
                    return (this.oscsEnabled - 1) << 1;

                case 0xe2:  // A/D converter
                    if (this.adc_func != null)
                        return this.adc_func.apply(this.adc_param, 0);
                    break;
            }
        }

        return 0;
    }

    public void write(int offset, int data) {

        if (offset < 0xe0) {
            int osc = offset & 0x1f;

            switch (offset & 0xe0) {
                case 0: // freq lo
                    this.oscillators[osc].freq &= 0xff00;
                    this.oscillators[osc].freq |= data;
                    break;

                case 0x20: // freq hi
                    this.oscillators[osc].freq &= 0x00ff;
                    this.oscillators[osc].freq |= data << 8;
                    break;

                case 0x40: // volume
                    this.oscillators[osc].vol = data;
                    break;

                case 0x60: // data - ignore writes
                    break;

                case 0x80: // wave table pointer
                    this.oscillators[osc].waveTblPointer = data << 8;
                    break;

                case 0xa0: // oscillator control
                    // if a fresh key-on, reset the accumulator
                    if ((this.oscillators[osc].control & 1) != 0 && ((data & 1) == 0)) {
                        this.oscillators[osc].accumulator = 0;
                    }
                    this.oscillators[osc].control = data;
                    break;

                case 0xc0: // bank select / wave table size / resolution
                    if ((data & 0x40) != 0) { // bank select - not used on the Apple IIgs
                        this.oscillators[osc].waveTblPointer |= 0x1_0000;
                    } else {
                        this.oscillators[osc].waveTblPointer &= 0xffff;
                    }

                    this.oscillators[osc].waveTblSize = (data >> 3) & 7;
                    this.oscillators[osc].wtSize = waveSizes[this.oscillators[osc].waveTblSize];
                    this.oscillators[osc].resolution = data & 7;
                    break;
            }
        } else { // global registers
            switch (offset) {
                case 0xe0: // interrupt status
                    break;

                case 0xe1: // oscillator enable
                    this.oscsEnabled = 1 + ((data >> 1) & 0x1f);

                    this.output_rate = (this.clock / 8) / (2 + this.oscsEnabled);
                    if (this.smpRateFunc != null)
                        this.smpRateFunc.accept(this.output_rate);
                    break;

                case 0xe2: // A/D converter
                    break;
            }
        }
    }

    public void writeRam(byte[] data, int offset, int length) {

        if (offset >= this.dramSize)
            return;
        if (offset + length > this.dramSize)
            length = this.dramSize - offset;

        if (length >= 0) System.arraycopy(data, 0, this.docRam, offset + 0, length);
    }

    public void setMuteMask(int muteMask) {
        for (int curChn = 0; curChn < 32; curChn++)
            this.oscillators[curChn].muted = (muteMask >> curChn) & 0x01;
    }

    /** set Sample Rate Change Callback routine */
    public void setCallback(Consumer<Integer> callbackFunc) {
        this.smpRateFunc = callbackFunc;
    }
}
