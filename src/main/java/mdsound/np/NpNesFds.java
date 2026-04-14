/*
 * NSFPlay/NFSPlug project by Brezza.
 *
 * https://web.archive.org/web/20160301201825/http://www.pokipoki.org/dsa/
 */

package mdsound.np;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import mdsound.np.Device.Counter;


/**
 * NpNesFds.
 *
 * @author Brezza
 * @author Valley Bell
 * @version Ported from NSFPlay 2.3 to VGMPlay (including C++ . C conversion) by Valley Bell on 26 September 2013
 */
public class NpNesFds {

    public static final double DEFAULT_CLOCK = 1789772.0;
    public static final int DEFAULT_RATE = 44100;

    private enum OPT {
        CUTOFF,
        RESET_4085,
        WRITE_PROTECT,
        END
    }

    private enum EG {
        EMOD,
        EVOL
    }

    private static final int RC_BITS = 12;

    private enum TG {
        TMOD,
        TWAV
    }

    // 8 bit approximation of master volume

    // max FDS vol vs max APU square (arbitrarily 1223)
    private static final double MASTER_VOL = 2.4 * 1223.0;
    // value that should map to master vol
    private static final double MAX_OUT = 32.0 * 63.0;
    private static final int[] MASTER = {
            (int) ((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 2.0f),
            (int) ((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 3.0f),
            (int) ((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 4.0f),
            (int) ((MASTER_VOL / MAX_OUT) * 256.0 * 2.0f / 5.0f)};

    private double rate, clock;
    private int mask;
    /** stereo mix */
    private final int[] sm = new int[2];
    /** current output */
    private int fOut;
    private final int[] option = new int[OPT.END.ordinal()];

    private boolean masterIo;
    public int masterVol;
    /** for track info */
    public int lastFreq;
    /** for track info */
    public int lastVol;

    /** two wave tables */
    public final int[][] wave = {new int[64], new int[64]};
    public final int[] freq = new int[2];
    private final int[] phase = new int[2];
    public boolean wavWrite;
    public boolean wavHalt;
    public boolean envHalt;
    public boolean modHalt;
    public int modPos;
    private int modWritePos;

    // two ramp envelopes
    public final boolean[] envMode = new boolean[2];
    public final boolean[] envDisable = new boolean[2];
    private final long[] envTimer = new long[2];
    public final int[] envSpeed = new int[2];
    public final int[] envOut = new int[2];
    public long masterEnvSpeed;

    // 1-pole RC low-pass filter
    private int rcAccum;
    private int rcK;
    private int rcL;

    private final Counter tickCount = new Counter();
    private long tickLast;

    public void setMask(int m) {
        this.mask = m & 1;
    }

    public void setStereoMix(int trk, int mixL, int mixR) {
        if (trk < 0) return;
        if (trk > 1) return;
        this.sm[0] = mixL;
        this.sm[1] = mixR;
    }

    public void setClock(double c) {
        this.clock = c;
    }

    public void setRate(double r) {
        double cutoff, leak;

        this.rate = r;

        this.tickCount.init(this.clock, this.rate);
        this.tickLast = 0;

        // configure lowpass filter
        cutoff = this.option[OPT.CUTOFF.ordinal()];
        leak = 0.0;
        if (cutoff > 0)
            leak = Math.exp(-2.0 * 3.14159 * cutoff / this.rate);
        this.rcK = (int) (leak * (double) (1 << RC_BITS));
        this.rcL = (1 << RC_BITS) - this.rcK;
    }

    public void setOption(int id, int val) {
        if (id < OPT.END.ordinal()) this.option[id] = val;

        // update cutoff immediately
        if (id == OPT.CUTOFF.ordinal()) setRate(this.rate);
    }

    public void reset() {

        this.masterIo = true;
        this.masterVol = 0;
        this.lastFreq = 0;
        this.lastVol = 0;

        this.rcAccum = 0;

        for (int i = 0; i < 2; ++i) {
            Arrays.fill(this.wave[i], 0);
            this.freq[i] = 0;
            this.phase[i] = 0;
        }
        this.wavWrite = false;
        this.wavHalt = true;
        this.envHalt = true;
        this.modHalt = true;
        this.modPos = 0;
        this.modWritePos = 0;

        for (int i = 0; i < 2; ++i) {
            this.envMode[i] = false;
            this.envDisable[i] = true;
            this.envTimer[i] = 0;
            this.envSpeed[i] = 0;
            this.envOut[i] = 0;
        }
        this.masterEnvSpeed = 0xff;

        // NOTE: the FDS BIOS reset only does the following related to audio:
        //   $4023 = $00
        //   $4023 = $83 enables master_io
        //   $4080 = $80 output volume = 0, envelope disabled
        //   $408A = $FF master envelope speed set to slowest
        write(0x4023, 0x00);
        write(0x4023, 0x83);
        write(0x4080, 0x80);
        write(0x408A, 0xff);

        // reset other stuff
        write(0x4082, 0x00); // wav freq 0
        write(0x4083, 0x80); // wav disable
        write(0x4084, 0x80); // mod strength 0
        write(0x4085, 0x00); // mod position 0
        write(0x4086, 0x00); // mod freq 0
        write(0x4087, 0x80); // mod disable
        write(0x4089, 0x00); // wav write disable, max Global volume}
    }

    public void tick(long clocks) {
        // clock envelopes
        if (!this.envHalt && !this.wavHalt && (this.masterEnvSpeed != 0)) {
            for (int i = 0; i < 2; ++i) {
                if (!this.envDisable[i]) {
                    this.envTimer[i] += clocks;
                    long period = ((this.envSpeed[i] + 1) * this.masterEnvSpeed) << 3;
                    while (this.envTimer[i] >= period) {
                        // clock the envelope
                        if (this.envMode[i]) {
                            if (this.envOut[i] < 32) ++this.envOut[i];
                        } else {
                            if (this.envOut[i] > 0) --this.envOut[i];
                        }
                        this.envTimer[i] -= period;
                    }
                }
            }
        }

        // clock the mod table
        if (!this.modHalt) {
            // advance phase, adjust for modulator
            int start_pos = this.phase[TG.TMOD.ordinal()] >> 16;
            this.phase[TG.TMOD.ordinal()] += clocks * this.freq[TG.TMOD.ordinal()];
            int end_pos = this.phase[TG.TMOD.ordinal()] >> 16;

            // wrap the phase to the 64-step table (+ 16 bit accumulator)
            this.phase[TG.TMOD.ordinal()] = this.phase[TG.TMOD.ordinal()] & 0x3f_ffff;

            // execute all clocked steps
            for (int p = start_pos; p < end_pos; ++p) {
                int wv = this.wave[TG.TMOD.ordinal()][p & 0x3f];
                if (wv == 4) // 4 resets mod position
                    this.modPos = 0;
                else {
                    int[] bias = {0, 1, 2, 4, 0, -4, -2, -1};
                    this.modPos += bias[wv];
                    this.modPos &= 0x7f; // 7-bit clamp
                }
            }
        }

        // clock the wav table
        if (!this.wavHalt) {
            // complex mod calculation
            int mod = 0;
            if (this.envOut[EG.EMOD.ordinal()] != 0) { // skip if modulator off
                // convert mod_pos to 7-bit signed
                int pos = (this.modPos < 64) ? this.modPos : (this.modPos - 128);

                // multiply pos by gain,
                // shift off 4 bits but with odd "rounding" behaviour
                int temp = pos * this.envOut[EG.EMOD.ordinal()];
                int rem = temp & 0x0f;
                temp >>= 4;
                if ((rem > 0) && ((temp & 0x80) == 0)) {
                    if (pos < 0) temp -= 1;
                    else temp += 2;
                }

                // wrap if range is exceeded
                while (temp >= 192) temp -= 256;
                while (temp < -64) temp += 256;

                // multiply result by pitch,
                // shift off 6 bits, round to nearest
                temp = this.freq[TG.TWAV.ordinal()] * temp;
                rem = temp & 0x3f;
                temp >>= 6;
                if (rem >= 32) temp += 1;

                mod = temp;
            }

            // advance wave table position
            int f = this.freq[TG.TWAV.ordinal()] + mod;
            this.phase[TG.TWAV.ordinal()] = (int) (this.phase[TG.TWAV.ordinal()] + (clocks * f));
            this.phase[TG.TWAV.ordinal()] = this.phase[TG.TWAV.ordinal()] & 0x3f_ffff; // wrap

            // store for trackInfo
            this.lastFreq = f;
        }

        // output volume caps at 32
        int volOut = this.envOut[EG.EVOL.ordinal()];
        if (volOut > 32) volOut = 32;

        // final output
        if (!this.wavWrite)
            this.fOut = this.wave[TG.TWAV.ordinal()][(this.phase[TG.TWAV.ordinal()] >> 16) & 0x3f] * volOut;

        // NOTE: during wav_halt, the unit still outputs (at phase 0)
        // and volume can affect it if the first sample is nonzero.
        // haven't worked out 100% of the conditions for volume to
        // effect (vol envelope does not seem to run, but am unsure)
        // but this implementation is very close to correct

        // store for track info
        this.lastVol = volOut;
    }

    public int render(int[] b) {

        this.tickCount.iup();
        long clocks = (this.tickCount.value() - this.tickLast) & 0xff;
        tick(clocks);
        this.tickLast = this.tickCount.value();

        int v = this.fOut * MASTER[this.masterVol] >> 8;

        // low-pass RC filter
        int rc_out = ((this.rcAccum * this.rcK) + (v * this.rcL)) >> RC_BITS;
        this.rcAccum = rc_out;
        v = rc_out;

        // output mix
        int m = this.mask != 0 ? 0 : v;
        b[0] = (m * this.sm[0]) >> 5;
        b[1] = (m * this.sm[1]) >> 5;
        return 2;
    }

    // w/o tick
    public int renderOrg(int[] b) {

        int v = this.fOut * MASTER[this.masterVol] >> 8;

        // low-pass RC filter
        int rc_out = ((this.rcAccum * this.rcK) + (v * this.rcL)) >> RC_BITS;
        this.rcAccum = rc_out;
        v = rc_out;

        // output mix
        int m = this.mask != 0 ? 0 : v;
        b[0] = (m * this.sm[0]) >> (7 - 3);
        b[1] = (m * this.sm[1]) >> (7 - 3);
        return 2;
    }

    public boolean write(int adr, int val) {

        // $4023 master I/O enable/disable
        if (adr == 0x4023) {
            this.masterIo = ((val & 2) != 0);
            if (!this.masterIo) this.fOut = 0; // KUMA: I want to stop it!
            return true;
        }

        if (!this.masterIo)
            return false;
        if (adr < 0x4040 || adr > 0x408a)
            return false;

        if (adr < 0x4080) { // $4040-407F wave table write
            if (this.wavWrite)
                this.wave[TG.TWAV.ordinal()][adr - 0x4040] = val & 0x3F;
            return true;
        }

        switch (adr & 0x00ff) {
        case 0x80: // $4080 volume envelope
            this.envDisable[EG.EVOL.ordinal()] = (val & 0x80) != 0;
            this.envMode[EG.EVOL.ordinal()] = (val & 0x40) != 0;
            this.envTimer[EG.EVOL.ordinal()] = 0;
            this.envSpeed[EG.EVOL.ordinal()] = val & 0x3f;
            if (this.envDisable[EG.EVOL.ordinal()])
                this.envOut[EG.EVOL.ordinal()] = this.envSpeed[EG.EVOL.ordinal()];
            return true;
        case 0x81: // $4081 ---
            return false;
        case 0x82: // $4082 wave frequency low
            this.freq[TG.TWAV.ordinal()] = (this.freq[TG.TWAV.ordinal()] & 0xf00) | val;
            return true;
        case 0x83: // $4083 wave frequency high / enables
            this.freq[TG.TWAV.ordinal()] = (this.freq[TG.TWAV.ordinal()] & 0x0ff) | ((val & 0x0f) << 8);
            this.wavHalt = (val & 0x80) != 0;
            this.envHalt = (val & 0x40) != 0;
            if (this.wavHalt)
                this.phase[TG.TWAV.ordinal()] = 0;
            if (this.envHalt) {
                this.envTimer[EG.EMOD.ordinal()] = 0;
                this.envTimer[EG.EVOL.ordinal()] = 0;
            }
            return true;
        case 0x84: // $4084 mod envelope
            this.envDisable[EG.EMOD.ordinal()] = (val & 0x80) != 0;
            this.envMode[EG.EMOD.ordinal()] = (val & 0x40) != 0;
            this.envTimer[EG.EMOD.ordinal()] = 0;
            this.envSpeed[EG.EMOD.ordinal()] = val & 0x3f;
            if (this.envDisable[EG.EMOD.ordinal()])
                this.envOut[EG.EMOD.ordinal()] = this.envSpeed[EG.EMOD.ordinal()];
            return true;
        case 0x85: // $4085 mod position
            this.modPos = val & 0x7F;
            // not hardware accurate., but prevents detune due to cycle inaccuracies
            // (notably in "Bio Miracle Bokutte Upa")
            if (this.option[OPT.RESET_4085.ordinal()] != 0)
                this.phase[TG.TMOD.ordinal()] = this.modWritePos << 16;
            return true;
        case 0x86: // $4086 mod frequency low
            this.freq[TG.TMOD.ordinal()] = (this.freq[TG.TMOD.ordinal()] & 0xf00) | val;
            return true;
        case 0x87: // $4087 mod frequency high / enable
            this.freq[TG.TMOD.ordinal()] = (this.freq[TG.TMOD.ordinal()] & 0x0ff) | ((val & 0x0f) << 8);
            this.modHalt = ((val & 0x80) != 0);
            if (this.modHalt)
                this.phase[TG.TMOD.ordinal()] = this.phase[TG.TMOD.ordinal()] & 0x3f_0000; // reset accumulator phase
            return true;
        case 0x88: // $4088 mod table write
            if (this.modHalt) {
                // writes to current playback position (there is no direct way to set phase)
                this.wave[TG.TMOD.ordinal()][(this.phase[TG.TMOD.ordinal()] >> 16) & 0x3f] = val & 0x7f;
                this.phase[TG.TMOD.ordinal()] = (this.phase[TG.TMOD.ordinal()] + 0x01_0000) & 0x3f_ffff;
                this.wave[TG.TMOD.ordinal()][(this.phase[TG.TMOD.ordinal()] >> 16) & 0x3f] = val & 0x7f;
                this.phase[TG.TMOD.ordinal()] = (this.phase[TG.TMOD.ordinal()] + 0x01_0000) & 0x3f_ffff;
                this.modWritePos = this.phase[TG.TMOD.ordinal()] >> 16; // used by OPT_4085_RESET
            }
            return true;
        case 0x89: // $4089 wave write enable, master volume
            this.wavWrite = (val & 0x80) != 0;
            this.masterVol = val & 0x03;
            return true;
        case 0x8A: // $408A envelope speed
            this.masterEnvSpeed = val;
            // haven't tested whether this register resets phase on hardware,
            // but this ensures my implementation won't spam envelope clocks
            // if this value suddenly goes low.
            this.envTimer[EG.EMOD.ordinal()] = 0;
            this.envTimer[EG.EVOL.ordinal()] = 0;
            return true;
        default:
            return false;
        }
    }

    public boolean read(int adr, /* ref */ int[] val) {

        if (adr >= 0x4040 && adr < 0x407F) {
            // TODO: if wav_write is not enabled, the
            // read address may not be reliable? need
            // to test this on hardware.
            val[0] = this.wave[TG.TWAV.ordinal()][adr - 0x4040];
            return true;
        }

        if (adr == 0x4090) { // $4090 read volume envelope
            val[0] = this.envOut[EG.EVOL.ordinal()] | 0x40;
            return true;
        }

        if (adr == 0x4092) { // $4092 read mod envelope
            val[0] = this.envOut[EG.EMOD.ordinal()] | 0x40;
            return true;
        }

        return false;
    }

    public void init(int clock, int rate) {

        this.option[OPT.CUTOFF.ordinal()] = 2000;
        this.option[OPT.RESET_4085.ordinal()] = 0;
        this.option[OPT.WRITE_PROTECT.ordinal()] = 0; // not used here, see nsfplay.cpp

        this.rcK = 0;
        this.rcL = (1 << RC_BITS);

        this.setClock(clock);
        this.setRate(rate);
        this.sm[0] = 128;
        this.sm[1] = 128;

        this.reset();
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("freq", this.lastFreq);
        map.put("vol", this.lastVol);
        map.put("wave", this.wave);

        map.put("VolDir", this.envMode[1]);
        map.put("VolSpd", this.envSpeed[1]);
        map.put("VolGain", this.envOut[1]);
        map.put("VolDi", this.envHalt);
        map.put("VolFrq", this.freq[1]);
        map.put("VolHlR", this.wavHalt);

        map.put("ModDir", this.envMode[0]);
        map.put("ModSpd", this.envSpeed[0]);
        map.put("ModGain", this.envOut[0]);
        map.put("ModDi", this.modHalt);
        map.put("ModFrq", this.freq[0]);
        map.put("ModCnt", this.modPos);

        map.put("EnvSpd", (int) this.masterEnvSpeed);
        map.put("EnvVolSw", !this.envDisable[1]);
        map.put("EnvModSw", !this.envDisable[0]);

        map.put("MasterVol", this.masterVol);
        map.put("WE", this.wavWrite);

        return map;
    }
}
