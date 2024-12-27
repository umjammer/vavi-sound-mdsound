/*
 * Copyright (C) 2004 Ki
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package mdsound.chips;


/**
 * Ootake (PC Engine emulator) PSG
 * <p>
 * <li>Simplified cue referencing. Improved tempo stability and sound quality.</li>
 * <li>No oversampling was used. (This is the author's subjective opinion,
 * but in the case of PSG, the beauty of the sound is often lost. Speed has also been improved.)</li>
 * <li>The sound quality and volume of the noise have been adjusted to match the actual device. v0.72</li>
 * <li>When 0x1F is written to the noise frequency,
 * it will sound at half the volume at the same frequency as 0x1E. v0.68</li>
 * <li>Currently, the playback sample rate is fixed at 44.1KHz (to increase the speed when playing CD-DA).</li>
 * <li>When the DDA sound finishes being produced, the waveform is faded out
 * instead of being abruptly set to 0, reducing noise. v0.57</li>
 * <li>Improved sound quality by cutting out the parts of the waveform data
 * with a lot of noise in DDA mode (sampling voice). Adjusted the volume as well. v0.59</li>
 * <li>The sound quality and volume of the noise has been adjusted to make it closer to
 * the atmosphere of the actual machine. v0.68</li>
 * <li>The initialization of waveIndex and the behavior in DDA mode have been revised
 * to be closer to the behavior of the actual device. v0.63</li>
 * <li>The wave table is now initialized when waveIndex is initialized. The sounds of "Fire Pro Wrestling",
 * "F1 Triple Battle", etc. are now closer to the real machine. v0.65</li>
 * <li>The positive and negative waveforms of the wave are now the same as in the real machine. v0.74</li>
 * <li>The minimum value of the wave has been set to -14 to improve the sound quality. v0.74</li>
 * <li>It seems that the critical section is not necessary (writing is not done simultaneously),
 * so it was omitted and the speed was improved. v1.09</li>
 * <li>Queue processing (ApuQueue.c) was integrated here to speed it up. v1.10</li>
 * <li>The volume of the bass region has been increased to make it easier to hear than the real thing. v1.46</li>
 * <li>Implemented LFO processing. The opening sound of "Haniinzakai"
 * and the sound effect of Flash Hiders are closer to the real machine sound. v1.59</li>
 * <p>
 * Copyright(C)2006-2012 Kitao Nakamura.
 *
 * <li>When releasing a modified or successor version, please be sure to include the source code.</li>
 * <li>In that case, we would appreciate it if you could let us know, even if it is after the fact.</li>
 * <li>Commercial use is prohibited.</li>
 * <li>The rest is subject to the GNU General Public License.</li>
 * <p>
 * <h3>[DEV NOTE]</h3>
 * <pre>
 *
 *   MAL   --- 0 - 15 (15 is -0 [dB], each decrease is -3.0 [dB])
 *   AL   --- 0 - 31 (31 is -0 [dB], each decrease is -1.5 [dB])
 *   LAL/RAL  --- 0 - 15 (15 is -0 [dB], each decrease is -3.0 [dB])
 *
 * </pre>
 * Let us reinterpret it as follows:
 * <pre>
 *
 *   MAL*2  --- 0 - 30 (30 is -0 [dB], each decrease is -1.5 [dB])
 *   AL   --- 0 - 31 (31 is -0 [dB], each decrease is -1.5 [dB])
 *   LAL/RAL*2 --- 0 - 30 (30 is -0 [dB], each decrease is -1.5 [dB])
 *
 * </pre>
 * <pre>
 *
 *   dB = 20 * log10(OUT/IN)
 *
 *   dB / 20 = log10(OUT/IN)
 *
 *   OUT/IN = 10^(dB/20)
 *
 * </pre>
 * If IN (maximum output) is 1.0,
 * <pre>
 *
 *     OUT = 10^(dB/20)
 *
 *                 -91 <= -(MAL*2 + AL + LAL(RAL)*2) <= 0
 *
 * </pre>
 * So the quietest sound is:
 * <pre>
 *
 *     -91 * 1.5 [dB] = -136.5 [dB] = 10^(-136.5/20) ~= 1.496236e-7 [times]
 *
 * </pre>
 * <p>
 * If we try to express a value of the order of 1e-7 in fixed point,
 * we need more than 24 bits just for the decimal part, and to handle 16-bit audio,
 * we need +16 bits, so we need 24+16 = 40 bits or more. Therefore,
 * it is difficult to express PCE audio in fixed point in a 32-bit processing system.
 * Therefore, we decided to use float for waveform calculations.
 * </p>
 * <pre>
 *
 *     It is the job of the APU to convert from float to the output format.
 *
 *     [2004.4.28] I decided to implement it using Sint32 after all (ignoring tiny values).
 *
 * </pre>
 * <p>
 * Although the CPU and PSG are packaged in the same IC,
 * it is safe to assume that the PSG actually operates at half the clock of the CPU.
 * Therefore, the operating frequency of the PSG, Fpsg, is:
 * <pre>
 *
 *     Fpsg = 21.47727 [MHz] / 3 / 2 = 3.579545 [MHz]
 *
 * </pre>
 * For example, when a waveform with 32 samples per cycle is played,
 * if you pick out each sample at this frequency, you get the magic number
 * <pre>
 *
 *     MPcm = 3579545 / 32 = 111860.78125 [Hz]
 *
 * </pre>
 * (the same as the NES).
 * <p>
 * However, since a song cannot be played with a fixed playback frequency,
 * the playback frequency is changed using a frequency parameter called FRQ.
 * FRQ is a 12-bit parameter written to the PSG register,
 * and is the "divisor" of the magic number obtained above.
 * </p><p>
 * When the waveform above, with 32 samples per cycle, is played back,
 * the frequency F of this waveform is calculated using FRQ as follows:
 * <pre>
 *
 *     F = MPcm / FRQ [Hz] (FRQ != 0)
 *
 * </pre>
 * If the PC playback sampling frequency is Fpc [Hz], the playback frequency F2
 * of a waveform with 32 samples per period is F2 = Fpc / 32 [Hz].
 * Therefore, the advance I of the counter that picks up one PCE sample for one PC sample is
 * <pre>
 *
 *     I = F / F2 = 32 * F / Fpc = Fpsg / FRQ / Fpc [unitless]
 *
 * <pre>
 * <h3>[NOISE CHANNEL]</h3>
 * <p>
 * A maximum length sequence (M sequence) is used to generate the pseudo-noise.
 * The bit length of the M sequence has not been investigated and is therefore unknown.
 * Here, implementation is carried out assuming 15 bits.
 * The output is one bit, and when D0 is zero it is a negative value, and when it is one it is a positive value.
 * </p><p>
 * For each sample of PC, pick out one sample of PCE.
 * The counter advance I is
 * <pre>
 *
 *     I = Fpsg / 64 / FRQ / Fpc  (FRQ != 0)
 *
 * </pre>
 * <h3>[Improving playback quality] 2004.6.22</h3>
 * <p>
 * The emulator does not know the next sound to be played until data is written to the Psg register.
 * When data is written to the register, we want to update the sound buffer, but unfortunately
 * in the current implementation, the sound buffer is updated in a separate thread,
 * so it cannot be updated at any time from the emulation thread.
 * <p>
 * In previous versions of the software, only the register settings at the time of
 * updating the sound buffer were valid, but this meant that, for example,
 * sounds that were output for only a moment between sound buffer updates were ignored.
 * This was especially problematic when using DDA mode or noise as a rhythm part.
 * <p>
 * In order to ensure that the values written to the registers are reflected in the audio output,
 * it would be possible to save the previously written register values
 * (when, which register, and what was written) and refer to these when updating the sound buffer.
 * How far back in time the register values should be saved probably depends on the length of the sound buffer,
 * but for now we will decide by trial and error.
 * <p>
 * The write operation to the Psg register is done in the emulation thread, and the sound buffer update is done
 * in its own thread. This causes an access conflict when the sound buffer update thread reads
 * from the register queue while the emulation thread is writing to it. To solve this problem,
 * <p>
 * <ol>
 *   <li>Do not update the sound buffer in a separate thread</li>
 *   <li>Use exclusive processing for queue access</li>
 * </ol>
 * <p>
 * There are two possible solutions.
 * For now, we will use the second solution.
 */
public class OotakeHuC6280 {

    private static final int N_CHANNEL = 6;

    /**
     * PSG does not oversample because the beauty of the sound is lost
     * when it is oversampled. Speed has also improved.
     *
     * @author Kitao
     */
    private static final double OVERSAMPLE_RATE = 1.0;
    /**
     * PSG volume reduction value. * 6.0 means dividing the sum of each channel.
     * The louder it is, the less sound there will be. Set it to a volume
     * that feels just right when CDDA is at 100%.
     * v2.19,v2.37,v2.39,v2.62 updated
     *
     * @author Kitao
     */
    private static final double PSG_DECLINE = 21.8500 * 6.0;
    /**
     * * If you change the value of PSG_DECLINE, you must also change the best value for the decay rate.
     * "Sparrow Detective Story 2" (if the negative value is small, the PSG becomes too prominent
     * and the ADPCM becomes difficult to hear),
     * "Makaimura" (If the minus sign is large, the sound will be muffled),
     * For "Soldier Blade", PSG_DECLINE = (14.4701*6.0), and a decay rate of around -1.0498779900db sounds
     * exceptionally good (subjective in my environment).
     * For "Moto Roader" (a slightly larger negative value is better)
     * and "1941" (a smaller negative value is better),
     * subtle changes in values can make a big difference.
     */
    private static final int NOISE_TABLE_VALUE_front = -18;
    /**
     * In terms of sharpness and ease of listening, -18:-1 was rated the best.
     * The larger the maximum value (closer to +), the heavier the sound will be.
     * The farther apart the two values, the heavier the sound will be.
     * Adjustments were made to "Formation Soccer" and the drums used in the ending theme of "Makaimura".
     * updated in v1.46,v2.40,v2.62
     */
    private static final int NOISE_TABLE_VALUE_rear = -1;
    /**
     * *The optimal value for this also changes depending on VOL_TABLE_DECLINE.
     * 0.30599899951. Kitao added. The amount of attenuation when muting a sampled sound.
     * Adjusted with the audio for "Soldier Blade" and "Shogi Beginners Not Required."
     * Generally, the smaller this value, the less noise there will be (although the opposite is also true).
     * This is an important value as it determines the tone of the sampling drum.
     * If the value is too high, the drums will sound weak on songs like
     * "Final Soldier", "Soldier Blade", and "Moto Roader."
     */
    private static final double SAMPLE_FADE_DECLINE = 0.305998999951;

    public class Psg {

        /**
         * -1.05809999010 is OK for "Sparrow Detective Story 2".
         * Added by Kitao. Decrease value of the volume table.
         * The larger the negative value, the harder it is to hear small sounds.
         * If the negative value is too small, the sound will be flat.
         * updated in v2.19,v2.37,v2.39,v2.40,v2.62,v2.65
         */
        private static final double VOL_TABLE_DECLINE = -1.05809999010;

        public int frq;
        private boolean on;
        public boolean dda;
        private int volume;
        public int volumeL;
        public int volumeR;
        public int outVolumeL;
        public int outVolumeR;
        public int[] wave = new int[32];
        private int waveIndex;
        private int ddaSample;
        private int phase;
        private int deltaPhase;
        public boolean bNoiseOn;
        public int noiseFrq;
        private int deltaNoisePhase;

        private boolean mute; // Added by Kitao. 1.29
        private int ddaFadeOutL; // Added by Kitao
        private int ddaFadeOutR; // Added by Kitao

        private static final int[] volumeTable = new int[92];

        /*
         * Creating a Volume Table.
         * Kitao updated.
         * Since low volume sounds are harder to hear than on the actual device,
         * the attenuation rate is set to VOL_TABLE_DECLINE[db]
         * (the best value found through trial and error) and normalization processing is performed. v1.46
         * The actual unit is probably also normalized when it is output through the amplifier.
         */
        static {
            volumeTable[0] = 0; // Added by Kitao
            for (int i = 1; i <= 91; i++) {
                double v = 91 - i;
                // VOL_TABLE_DECLINE: If it is set too small, the sound tends to become flat.
                // Adjusted with "Soldier Blade". v1.46.
                volumeTable[i] = (int) (32768.0 * Math.pow(10.0, v * VOL_TABLE_DECLINE / 20.0));
            }
        }

        Psg(boolean ch3) {
            // Kitao updated. v0.65. Initialized wave data.
            for (int j = 0; j < 32; j++)
                this.wave[j] = -14; // Initialized with minimum value. Required for "Fire Pro Wrestling", "Formation Soccer '90", and "F1 Triple Battle".
            if (ch3) {
                for (int j = 0; j < 32; j++)
                    this.wave[j] = 17; // Channel 3 is initialized to the maximum value. "F1 Triple Battle". v2.65
            }
        }

        private void mainVolume() {
            outVolumeL = volumeTable[volume + (mainVolumeL + volumeL) * 2];
            outVolumeR = volumeTable[volume + (mainVolumeR + volumeR) * 2];
        }

        private void reset() {
            this.volume = 0;
            this.outVolumeL = 0;
            this.outVolumeR = 0;
            this.ddaFadeOutL = 0;
            this.ddaFadeOutR = 0;
        }

        // Added by Kitao
        private void setMute(boolean mute) {
            this.mute = mute;
            if (mute) {
                this.ddaFadeOutL = 0;
                this.ddaFadeOutR = 0;
            }
        }

        private void noise(int data) {
            this.bNoiseOn = ((data & 0x80) != 0);
            this.noiseFrq = 0x1F - (data & 0x1F);
            if (this.noiseFrq == 0)
                this.deltaNoisePhase = (int) ((2048.0 * resampleRate) + 0.5); // Kitao updated
            else
                this.deltaNoisePhase = (int) ((2048.0 * resampleRate) / (double) this.noiseFrq + 0.5); // Kitao updated
        }

        // Kitao updated. Wave data is updated even in DDA mode. v0.63. "Fire Pro Wrestling"
        private void porcessWave(int data) {
            data &= 0x1F;
            waveCrash = false; // Added by Kitao.
            if (!this.on) { // Added by Kitao. Update Wave data only when sound is not being played. v0.65. Engine sound from "F1 Triple Battle".
                this.wave[this.waveIndex++] = 17 - data; // 17: Kitao updated. The value that resonates most comfortably. Adjusted for "Mizubaku Adventure", "Moto Roader", "Dragon Spirit", etc.
                this.waveIndex &= 0x1F;
            }
            if (this.dda) {
                // Kitao updated. To reduce noise, values below 6 are cut. v0.59
                if (data < 6) // set to 6 by "Cyber Night"
                    data = 6; // There is a lot of noise, so small values are cut.
                this.ddaSample = 11 - data; // 11 by "Cyber Night". The drum sounds are the best. v0.74

                if (!this.on) // When Wave data is rewritten in DDA mode
                    waveCrash = true;
            }
        }

        private void setOnDdaAl(int data) {
            if (honeyInTheSky) { // When pausing during "Honey in the Sky", a slight noise occurs due to a subtle problem with the volume adjustment timing, so this is currently being addressed with a patch. updated in v2.60
                if ((this.on) && (data == 0)) { // If data is 0 while speaking, the LR volume is also reset to 0. The noise during the pause of "Honey in the Sky" is resolved. If you reset when only (data & 0x1F) is 0, it will not work with "Silent Debuggers" etc. If you reset when not speaking, it will not work with "Atomic Robo". v2.55
//logger.log(Level.TRACE, "test %X %X %X %X".formatted(this.Channel, this.bOn, this.MainVolumeL, this.MainVolumeR));
                    if ((mainVolumeL & 1) == 0) // Processes only when bit 0 of the main volume is 0 (irregular 0xE in "Honey in the Sky". 0xF in other games. * "Heavy Unit" was also 0xE). Without this, there will be no sound in "Mizubaku Great Adventure". Not confirmed if this is the same as the mechanism of the actual machine. Added in v2.53
                        this.volumeL = 0;
                    if ((mainVolumeR & 1) == 0) // The same applies to the right channel.
                        this.volumeR = 0;
                }
            }

            this.on = ((data & 0x80) != 0);
            if ((this.dda) && ((data & 0x40) == 0)) { // When switching from DDA to WAVE or muting from DDA
                // Added by Kitao. If you suddenly mute the DDA, noticeable noise will be introduced, so it will fade out.
                int i = 1 + (1 >> 3) + (1 >> 4) + (1 >> 5) + (1 >> 7) + (1 >> 12) + (1 >> 14) + (1 >> 15);
                this.ddaFadeOutL = (int) ((double) (this.ddaSample * this.outVolumeL) *
                        (i * SAMPLE_FADE_DECLINE)); // Original volume. updated in v2.65
                this.ddaFadeOutR = (int) ((double) (this.ddaSample * this.outVolumeR) *
                        (i * SAMPLE_FADE_DECLINE));

            }
            this.dda = ((data & 0x40) != 0);

            // Added by Kitao. Resets the Wave index when bits 7 and 6 of data are 01.
            // If you have written Wave data in DDA mode, you can restore (initialize) the Wave data here. "Fire Pro Wrestling".
            if ((data & 0xC0) == 0x40) {
                this.waveIndex = 0;
                if (waveCrash) {
                    for (int i = 0; i < 32; i++)
                        this.wave[i] = -14; // Initialize Wave data to minimum value
                    waveCrash = false;
                }
            }

            this.volume = data & 0x1F;
            this.mainVolume();
        }

        /**
         * @param l OUT
         * @param r OUT
         */
        private void mix(int c, int[] l, int[] r) {
            if ((this.on) && ((c != 1) || (lfoControl == 0)) && (!this.mute)) { // Kitao updated
                // Added by Kitao. for DDA volume and noise volume calculation.
                int smp;
                if (this.dda) {
                    smp = this.ddaSample * this.outVolumeL;
                    // Kitao updated. The volume of the sampled sounds has been adjusted to match the real machine. Re-adjusted in v2.39, v2.40, v2.62, and v2.65.
                    l[0] += smp + (smp >> 3) + (smp >> 4) + (smp >> 5) + (smp >> 7) + (smp >> 12) + (smp >> 14) + (smp >> 15);
                    smp = this.ddaSample * this.outVolumeR;
                    // Kitao updated. The volume of the sampled sounds has been adjusted to match the real machine. Re-adjusted in v2.39, v2.40, v2.62, and v2.65.
                    r[0] += smp + (smp >> 3) + (smp >> 4) + (smp >> 5) + (smp >> 7) + (smp >> 12) + (smp >> 14) + (smp >> 15);
                } else if (this.bNoiseOn) {
                    // Added by Kitao
                    int sample = noiseTable[this.phase >>> 17];

                    if (this.noiseFrq == 0) {
                        // Added by Kitao. When noiseFrq=0 (0x1F is written to data), the volume is half the normal volume.
                        // ("Fire Pro Wrestling 3", "Pac-Land", "Momotaro Action", "Ganbare Golf Boys", etc.)
                        smp = sample * this.outVolumeL;
                        l[0] += (smp >> 1) + (smp >> 12) + (smp >> 14); // (1/2 + 1/4096 + (1/32768 + 1/32768))
                        smp = sample * this.outVolumeR;
                        r[0] += (smp >> 1) + (smp >> 12) + (smp >> 14);
                    } else { // Normal
                        smp = sample * this.outVolumeL;
                        // Kitao updated. Adjusted the noise volume to match the actual device (1 + 1/2048 + 1/16384 + 1/32768)
                        // This "+1/32768" is perfect (subjective. "Dai Makaimura" and "Soldier Blade" etc.). Updated in v2.62
                        l[0] += smp + (smp >> 11) + (smp >> 14) + (smp >> 15);
                        smp = sample * this.outVolumeR;
                        // Kitao updated. Adjusted the noise volume to match the actual device
                        r[0] += smp + (smp >> 11) + (smp >> 14) + (smp >> 15);
                    }

                    this.phase += this.deltaNoisePhase; // Kitao updated
                } else if (this.deltaPhase != 0) {
                    // Kitao updated. No oversampling was done.
                    int sample = this.wave[this.phase >>> 27];
                    if (this.frq < 128)
                        sample -= sample >> 2; // The volume of the low frequency range has been limited. The sound at the start of "Blood Gear" is now the same as the real machine. "Soldier Blade" and other games are now closer to the real machine. v2.03

                    l[0] += sample * this.outVolumeL; // Kitao updated
                    r[0] += sample * this.outVolumeR; // Kitao updated

                    // Kitao updated. Lfo On is now enabled, and the Lfo effect is closer to that of the real device. v1.59
                    if ((c == 0) && (lfoControl > 0)) {
                        // When _LfoCtrl is 1 and shifts 0 times (as is), "Honey in the Sky" sounds closer to the actual instrument.
                        // When _LfoCtrl is 3 and there are 4 shifts, the "Flash Hiders" sound is closer to the real thing.
                        int lfo = psgs[1].wave[psgs[1].phase >> 27] << ((lfoControl - 1) << 1); // Updated in v1.60
                        psgs[0].phase += (int) ((65536.0 * 256.0 * 8.0 * resampleRate) / (double) (psgs[0].frq + lfo) + 0.5);
                        psgs[1].phase += (int) ((65536.0 * 256.0 * 8.0 * resampleRate) / (double) (psgs[1].frq * lfoFreq) + 0.5); // Updated in v1.60
                    } else
                        this.phase += this.deltaPhase;
                }
            }
            // Added by Kitao. When the DDA is muted, the sound is muted by fading out to reduce noise.
            // It is effective in "Berabou Man" (a few seconds after "I'm the Doctor Bakuda"), "Power Tennis" (a few seconds after the title song ends, when the score is called out), and "Shogi Beginners Not Allowed" (audio), etc.
            if (this.ddaFadeOutL > 0)
                --this.ddaFadeOutL;
            else if (this.ddaFadeOutL < 0)
                ++this.ddaFadeOutL;
            if (this.ddaFadeOutR > 0)
                --this.ddaFadeOutR;
            else if (this.ddaFadeOutR < 0)
                ++this.ddaFadeOutR;
            l[0] += this.ddaFadeOutL;
            r[0] += this.ddaFadeOutR;
        }
    }

    private final double sampleRate;
    private final double psgFreq;
    private final double resampleRate;

    private final Psg[] psgs = new Psg[8]; // 6, 7 is unused
    private int channel; // 0 - 5;
    public int mainVolumeL; // 0 - 15
    public int mainVolumeR; // 0 - 15
    public int lfoFreq;
    /** Not used since v1.59. Retained for state loading of previous versions. */
    private final boolean lfoOn = false;
    public int lfoControl;
    /** Not used since v1.59. Retained for state loading of previous versions. */
    private final int lfoShift = 0;
    /** Added by Kitao. */
    private final int psgVolumeEffect;
    /** Added by Kitao. */
    private double volume;
    /** Added by Kitao. v1.08 */
    private double vol;

    /** Added by Kitao. true if Wave data is rewritten during DDA playback */
    private boolean waveCrash;
    /**
     * For "Honey in the Sky" patch.
     * @since 2.60
     */
    private boolean honeyInTheSky;

    // for debug purpose
    private final byte[] port = new byte[16];

    private static final int[] noiseTable = new int[32768];

    /*
     * Creating a Noise Table
     */
    static {
        int reg = 0x100;

        for (int i = 0; i < 32768; i++) {
            int bit0 = reg & 1;
            int bit1 = (reg & 2) >> 1;
            int bit14 = (bit0 ^ bit1);
            reg >>= 1;
            reg |= (bit14 << 14);
            // Kitao updated. The volume and sound quality of the noise have been adjusted.
            noiseTable[i] = (bit0 != 0) ? NOISE_TABLE_VALUE_front : NOISE_TABLE_VALUE_rear;
        }
    }

    /**
     * Describes the behavior for writing to the Psg port.
     */
    public void writeReg(int reg, int data) {
        Psg psg;

        this.port[reg & 15] = (byte) data;

        switch (reg & 15) {
            case 0: // register select
                this.channel = data & 7;
                break;

            case 1: // main volume
                this.mainVolumeL = (data >> 4) & 0x0F;
                this.mainVolumeR = data & 0x0F;

                /* LMAL, RMAL affect the volume of all channels */
                for (int c = 0; c < N_CHANNEL; c++) {
                    psg = this.psgs[c];
                    psg.mainVolume();
                }
                break;

            case 2: // frequency low
                psg = this.psgs[this.channel];
                psg.frq &= ~(int) 0xff;
                psg.frq |= data;
                // Kitao To increase speed, updated.update_frequency is now executed directly rather than as a subroutine.
                int frq = (psg.frq - 1) & 0xffF;
                if (frq != 0)
                    // Kitao updated. To increase speed, all calculations except frq are constants.
                    // To improve accuracy, we divide by the smaller value of OVERSAMPLE_RATE first.
                    // +0.5 is rounded off to improve accuracy and reduce noise.
                    psg.deltaPhase = (int) ((65536.0 * 256.0 * 8.0 * this.resampleRate) / (double) frq + 0.5);
                else
                    psg.deltaPhase = 0;
                break;

            case 3: // frequency high
                psg = this.psgs[this.channel];
                psg.frq &= ~(int) 0xF00;
                psg.frq |= (data & 0x0F) << 8;
                // Kitao To increase speed, updated.update_frequency is now executed directly rather than as a subroutine.
                frq = (psg.frq - 1) & 0xffF;
                if (frq != 0)
                    // Kitao updated. To increase speed, all calculations except frq are constants.
                    // To improve accuracy, we divide by the smaller value of OVERSAMPLE_RATE first.
                    // +0.5 is rounded off to improve accuracy and reduce noise.
                    psg.deltaPhase = (int) ((65536.0 * 256.0 * 8.0 * this.resampleRate) / (double) frq + 0.5);
                else
                    psg.deltaPhase = 0;
                break;

            case 4: // ON, DDA, AL
                psg = this.psgs[this.channel];
                psg.setOnDdaAl(data);
                break;

            case 5: // LAL, RAL
                psg = this.psgs[this.channel];
                psg.volumeL = (data >> 4) & 0xF;
                psg.volumeR = data & 0xF;
                psg.mainVolume();
                break;

            case 6: // wave data
                psg = this.psgs[this.channel];
                psg.porcessWave(data);
                break;

            case 7: // noise on, noise frq
                if (this.channel >= 4) {
                    psg = this.psgs[this.channel];
                    psg.noise(data);
                }
                break;

            case 8: // LFO frequency
                this.lfoFreq = data;
                // for test by Kitao
//logger.log(Level.TRACE, "LFO Frq = %X".formatted(this.LfoFrq));
                break;

            case 9: // LFO control
                // Kitao updated. I tried to implement it simply. I haven't confirmed if it works the same on the actual device. I implemented it to make the sound of "Honey in the Sky" sound similar. v1.59
                if ((data & 0x80) != 0) { // If you set bit 7 and call it, it will probably reset.
                    this.psgs[1].phase = 0; // LfoFrq is not initialized. "Honey in the Sky"
//logger.log(Level.TRACE, "LFO control = %X".formatted(data));
                }
                this.lfoControl = data & 7; // "Drop Rock Hora Hora" uses 5. v1.61 update
                if ((this.lfoControl & 4) != 0)
                    this.lfoControl = 0; // "Drop Rock Hora Hora" When I listened to it on the actual machine, it sounded the same as with LFO off, so since bit 2 was set (treated as a negative number?), I decided to treat it as 0.
//logger.log(Level.TRACE, "LFO control = %X,  Frq =%X".formatted(data, this.LfoFrq));
                break;

            default: // invalid write
                break;
        }
    }

    // Added by Kitao.
    private void setVOL() {
        if (this.psgVolumeEffect == 0)
            //this.VOL = 0.0; // mute
            this.vol = 1.0 / 128.0;
        else if (this.psgVolumeEffect == 3)
            // 3/4: updated in v1.29
            this.vol = this.volume / (OVERSAMPLE_RATE * 4.0 / 3.0);
        else
            // Added by Kitao. _PsgVolumeEffect=Volume adjustment effect.
            this.vol = this.volume / (OVERSAMPLE_RATE * this.psgVolumeEffect);
    }

    /**
     * Mixes the output of Psg.
     *
     * @param buffer  Output buffer. Kitao updated: Changed to Sint16 since it is a PSG-only buffer.
     * @param samples Number of samples to write
     */
    public void mix(int[][] buffer, int samples) {

        for (int i = 0; i < samples; i++) {
            // Added by Kitao. A buffer for adding 6ch samples. Necessary to maintain accuracy.
            // After the total for all 6 channels has been calculated, this is converted to Sint16 and written.
            int[] sampleAllL = new int[] {0};
            // Added by Kitao. R channel of the above.
            int[] sampleAllR = new int[] {0};
            for (int c = 0; c < N_CHANNEL; c++) {
                Psg psg = this.psgs[c];
                psg.mix(c, sampleAllL, sampleAllR);
            }
            // Kitao updated. Once the 6 channels are combined, adjust the volume and write it to the buffer.
            sampleAllL[0] = (int) ((double) sampleAllL[0] * this.vol);
            //if ((sampleAllL>32767)||(sampleAllL<-32768)) logger.log(Level.DEBUG, "Psg is saturated!"); // for test
            //  if (sampleAllL> 32767) sampleAllL= 32767; // The saturation check is required since the Vol. has been updated. v2.39
            //  if (sampleAllL<-32768) sampleAllL=-32768; // This only happens when you get hit by a UFO in "Pac-Land" and doesn't happen in normal games. "Bikkuri-Man World" with its loud volume is also OK. "Pac-Land" is usually OK and even when saturated, it's only slight, so it's fine sound quality-wise.
            // So, in terms of sound quality, the PSG should be split into two DirectX channels (this would require heavier processing), but currently "Pac-Land" can be played without any sound quality issues using only saturation processing (speed is prioritized).
            sampleAllR[0] = (int) ((double) sampleAllR[0] * this.vol);
            //if ((sampleAllR>32767)||(sampleAllR<-32768)) logger.log(Level.DEBUG, "Psg is saturated!"); // for test
            //  if (sampleAllR> 32767) sampleAllR= 32767; // The saturation check is required since the Vol. has been updated. v2.39
            //  if (sampleAllR<-32768) sampleAllR=-32768; //
            buffer[0][i] = sampleAllL[0] << 1;
            buffer[1][i] = sampleAllR[0] << 1;
        }
    }

    // Kitao updated
    public void reset() {
        for (int c = 0; c < 8; c++) {
            this.psgs[c] = new Psg(c == 3);
        }

        this.mainVolumeL = 0;
        this.mainVolumeR = 0;
        this.lfoFreq = 0;
        this.lfoControl = 0;
        this.channel = 0; // Added by Kitao. v2.65
        this.waveCrash = false; // Added by Kitao.
    }

    /**
     * Initialize Psg.
     */
    public OotakeHuC6280(int clock, int sampleRate) {

        this.psgFreq = clock & 0x7fff_ffff;
        setHoneyInTheSky(((clock >> 31) & 0x01) != 0);

        this.psgVolumeEffect = 0;
        this.volume = 0;
        this.vol = 0.0;

        setVolume(); // Added by Kitao.

        reset();

        this.sampleRate = sampleRate;
        this.resampleRate = this.psgFreq / OVERSAMPLE_RATE / this.sampleRate;
    }

    /**
     * Describes the behavior for reading the Psg port.
     */
    public int read(int regNum) {
        if (regNum == 0)
            return this.channel;

        return this.port[regNum & 15] & 0xff;
    }

    /**
     * Describes the behavior for writing to the Psg port.
     */
    public void write(int regNum, int data) {
        writeReg(regNum, data);
    }

    /**
     * @author Kitao
     * Added by Kitao. The PSG volume can also be set individually.
     */
    public void setVolume() {
        this.volume = 1.0 / PSG_DECLINE;
        setVOL();
    }

    /** @author Kitao */
    public void resetVolumeReg() {
        this.mainVolumeL = 0;
        this.mainVolumeR = 0;
        for (int c = 0; c < N_CHANNEL; c++) {
            this.psgs[c].reset();
        }
    }

    public void setMuteMask(int muteMask) {
        for (byte c = 0x00; c < N_CHANNEL; c++)
            this.psgs[c].setMute(((muteMask >> c) & 0x01) != 0);
    }

    /** @author Kitao */
    public boolean getMutePsgChannel(int c) {
        return this.psgs[c].mute;
    }

    /**
     * @author Kitao
     * @since v2.60
     */
    public void setHoneyInTheSky(boolean bHoneyInTheSky) {
        this.honeyInTheSky = bHoneyInTheSky;
    }

    public Psg getPsg(int ch) {
        return psgs[ch];
    }
}
