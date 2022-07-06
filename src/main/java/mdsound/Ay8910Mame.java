/*
 * from libvgm  https://github.com/ValleyBell/libvgm/blob/7f460775717c6287827aa7f13a4599f9c95b7a11/emu/cores/ay8910.c
 *
 * license:BSD-3-Clause
 * copyright-holders:Couriersud
 */

package mdsound;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import vavi.util.Debug;


public class Ay8910Mame extends Instrument.BaseInstrument {

    private Ay8910Context[] chip = new Ay8910Context[] {new Ay8910Context(), new Ay8910Context()};
    private static final int DefaultAY8910ClockValue = 1789750;
    private int sampleRate = 44100;
    private int masterClock = DefaultAY8910ClockValue;
    private double sampleCounter = 0;
    private int[][] frm = new int[][] {new int[1], new int[1]};
    private int[][] before = new int[][] {new int[1], new int[1]};

    @Override
    public String getName() {
        return "AY8910mame";
    }

    @Override
    public String getShortName() {
        return "AY10m";
    }

    @Override
    public void reset(byte chipId) {
        chip[chipId].reset();
    }

    @Override
    public int start(byte chipId, int clock) {
        return start(chipId, clock, DefaultAY8910ClockValue);
    }

    @Override
    public int start(byte chipId, int clock, int clockValue, Object... option) {
        Ay8910Context ch = new Ay8910Context();
        sampleRate = clock;
        masterClock = clockValue / 4;
        ch.init(clockValue, (byte) 0, (byte) 0);

        chip[chipId] = ch;

        visVolume = new int[2][][];
        visVolume[0] = new int[2][];
        visVolume[1] = new int[2][];
        visVolume[0][0] = new int[2];
        visVolume[1][0] = new int[2];
        visVolume[0][1] = new int[2];
        visVolume[1][1] = new int[2];

        return clock;
    }

    @Override
    public void stop(byte chipId) {
        chip[chipId].stop();
    }

    @Override
    public void update(byte chipId, int[][] outputs, int samples) {
        for (int i = 0; i < samples; i++) {
            outputs[0][i] = 0;
            outputs[1][i] = 0;

            sampleCounter += (double) masterClock / sampleRate;
            int upc = (int) sampleCounter;
            while (sampleCounter >= 1) {
                chip[chipId].updateOne(1, frm);

                outputs[0][i] += frm[0][0];
                outputs[1][i] += frm[1][0];

                sampleCounter -= 1.0;
            }

            if (upc != 0) {
                outputs[0][i] /= upc;
                outputs[1][i] /= upc;
                before[0][i] = outputs[0][i];
                before[1][i] = outputs[1][i];
            } else {
                outputs[0][i] = before[0][i];
                outputs[1][i] = before[1][i];
            }

            //outputs[0][i] <<= 0;
            //outputs[1][i] <<= 0;
        }

        visVolume[chipId][0][0] = outputs[0][0];
        visVolume[chipId][0][1] = outputs[1][0];
    }

    @Override
    public int write(byte chipId, int port, int adr, int data) {
        chip[chipId].writeReg((byte) adr, (byte) data);
        return 0;
    }

    public void setMute(byte chipId, int mask) {
        chip[chipId].setMuteMask((byte) mask);
    }

    /*
     * Couriersud, July 2014:
     *
     * This documents recent work on the AY8910. A YM2149 is now on it's way from
     * Hong Kong as well.
     *
     * TODO:
     *
     * - Create a true Sound device nAY8910 driver.
     * - implement approach outlined below in this driver.
     *
     * For years I had a AY8910 in my drawer. Arduinos were around as well.
     * Using the approach documented in this blog post
     *    http://www.986-studio.com/2014/05/18/another-ay-entry/#more-476
     * I measured the output voltages using a Extech 520.
     *
     * Measurement Setup
     *
     * Laptop <--> Arduino <---> AY8910
     *
     * AY8910 Registers:
     * 0x07: 3f
     * 0x08: RV
     * 0x09: RV
     * 0x0A: RV
     *
     * Output was measured on Analog Output B with a resistor RD to
     * ground.
     *
     * Measurement results:
     *
     * RD      983  9.830k   99.5k  1.001M    open
     *
     * RV        B       B       B       B       B
     *  0   0.0000  0.0000  0.0001  0.0011  0.0616
     *  1   0.0106  0.0998  0.6680  1.8150  2.7260
     *  2   0.0150  0.1377  0.8320  1.9890  2.8120
     *  3   0.0222  0.1960  1.0260  2.1740  2.9000
     *  4   0.0320  0.2708  1.2320  2.3360  2.9760
     *  5   0.0466  0.3719  1.4530  2.4880  3.0440
     *  6   0.0665  0.4938  1.6680  2.6280  3.1130
     *  7   0.1039  0.6910  1.9500  2.7900  3.1860
     *  8   0.1237  0.7790  2.0500  2.8590  3.2340
     *  9   0.1986  1.0660  2.3320  3.0090  3.3090
     * 10   0.2803  1.3010  2.5050  3.0850  3.3380
     * 11   0.3548  1.4740  2.6170  3.1340  3.3590
     * 12   0.4702  1.6870  2.7340  3.1800  3.3730
     * 13   0.6030  1.8870  2.8410  3.2300  3.4050
     * 14   0.7530  2.0740  2.9280  3.2580  3.4170
     * 15   0.9250  2.2510  3.0040  3.2940  3.4380
     *
     * Using an equivalent model approach with two resistors
     *
     *      5V
     *       |
     *       Z
     *       Z Resistor Value for RV
     *       Z
     *       |
     *       +---> Output signal
     *       |
     *       Z
     *       Z External RD
     *       Z
     *       |
     *      GND
     *
     * will NOT work out of the box since RV = RV(RD).
     *
     * The following approach will be used going forward based on die pictures
     * of the AY8910 done by Dr. Stack van Hay:
     *
     *
     *              5V
     *             _| D
     *          G |      NMOS
     *     Vg ---||               Kn depends on volume selected
     *            |_  S Vs
     *               |
     *               |
     *               +---> VO Output signal
     *               |
     *               Z
     *               Z External RD
     *               Z
     *               |
     *              GND
     *
     *  Whilst conducting, the FET operates in saturation mode:
     *
     *  Id = Kn * (Vgs - Vth)^2
     *
     *  Using Id = Vs / RD
     *
     *  Vs = Kn * RD  * (Vg - Vs - Vth)^2
     *
     *  finally using Vg' = Vg - Vth
     *
     *  Vs = Vg' + 1 / (2 * Kn * RD) - sqrt((Vg' + 1 / (2 * Kn * RD))^2 - Vg'^2)
     *
     *  and finally
     *
     *  VO = Vs
     *
     *  and this can be used to re-Thenevin to 5V
     *
     *  RVequiv = RD * ( 5V / VO - 1)
     *
     *  The RV and Kn parameter are derived using least squares to match
     *  calculation results with measurements.
     *
     *  FIXME:
     *  There is voltage of 60 mV measured with the EX520 (Ri ~ 10M). This may
     *  be induced by cutoff currents from the 15 FETs.
     *
     */
    /*

  ay8910.c

  Emulation of the AY-3-8910 / YM2149 Sound this.

  Based on various code snippets by Ville Hallik, Michael Cuddy,
  Tatsuyuki Satoh, Fabrice Frances, Nicola Salmoria.

  Mostly rewritten by couriersud in 2008

  Public documentation:

  - http://privatfrickler.de/blick-auf-den-chip-soundchip-general-instruments-ay-3-8910/
    Die pictures of the AY8910

  - US Patent 4933980

  Games using ADSR: gyruss

  A list with more games using ADSR can be found here:
        http://mametesters.org/view.php?id=3043

  TODO:
  * The AY8930 has an extended mode which is currently
    not emulated.
  * YM2610 & YM2608 will need a separate flag in their config structures
    to distinguish between legacy and discrete mode.

  The rewrite also introduces a generic model for the DAC. This model is
  not perfect, but allows channel mixing based on a parametrized approach.
  This model also allows to factor in different loads on individual channels.
  If a better model is developped in the future or better measurements are
  available, the driver should be easy to change. The model is described
  later.

  In order to not break hundreds of existing drivers by default the flag
  AY8910_LEGACY_OUTPUT is used by drivers not changed to take into account the
  new model. All outputs are normalized to the old output range (i.e. 0 .. 7ffff).
  In the case of channel mixing, output range is 0...3 * 7fff.

  The main difference between the AY-3-8910 and the YM2149 is, that the
  AY-3-8910 datasheet mentions, that fixed volume level 0, which is set by
  registers 8 to 10 is "channel off". The YM2149 mentions, that the generated
  signal has a 2V DC component. This is confirmed by measurements. The approach
  taken here is to assume the 2V DC offset for all outputs for the YM2149.
  For the AY-3-8910, an offset is used if envelope is active for a channel.
  This is backed by oscilloscope pictures from the datasheet. If a fixed volume
  is set, i.e. envelope is disabled, the output voltage is set to 0V. Recordings
  I found on the web for gyruss indicate, that the AY-3-8910 offset should
  be around 0.2V. This will also make Sound levels more compatible with
  user observations for scramble.

  The Model:
                     5V     5V
                      |      |
                      /      |
  Volume Level x >---|       Z
                      >      Z Pullup Resistor RU
                       |     Z
                       Z     |
                    Rx Z     |
                       Z     |
                       |     |
                       '-----+-------->  >---+----> Output signal
                             |               |
                             Z               Z
               Pulldown RD   Z               Z Load RL
                             Z               Z
                             |               |
                            GND             GND

Each Volume level x will select a different resistor Rx. Measurements from fpgaarcade.com
where used to calibrate channel mixing for the YM2149. This was done using
a least square approach using a fixed RL of 1K Ohm.

For the AY measurements cited in e.g. openmsx as "Hacker Kay" for a single
channel were taken. These were normalized to 0 ... 65535 and consequently
adapted to an offset of 0.2V and a VPP of 1.3V. These measurements are in
line e.g. with the formula used by pcmenc for the volume: vol(i) = exp(i/2-7.5).

The following is documentation from the code moved here and amended to reflect
the changes done:

Careful studies of the chip output prove that the chip counts up from 0
until the counter becomes greater or equal to the period. This is an
important difference when the program is rapidly changing the period to
modulate the Sound. This is worthwhile noting, since the datasheets
say, that the chip counts down.
Also, note that period = 0 is the same as period = 1. This is mentioned
in the YM2203 data sheets. However, this does NOT apply to the Envelope
period. In that case, period = 0 is half as period = 1.

Envelope shapes:
    C AtAlH
    0 0 x x  \___
    0 1 x x  /___
    1 0 0 0  \\\\
    1 0 0 1  \___
    1 0 1 0  \/\/
    1 0 1 1  \```
    1 1 0 0  ////
    1 1 0 1  /```
    1 1 1 0  /\/\
    1 1 1 1  /___

The envelope counter on the AY-3-8910 has 16 steps. On the YM2149 it
has twice the steps, happening twice as fast.

****************************************************************************

    The bus control and chip selection signals of the AY PSGs and their
    pin-compatible clones such as YM2149 are somewhat unconventional and
    redundant, having been designed for compatibility with GI's CP1610
    series of microprocessors. Much of the redundancy can be finessed by
    tying BC2 to Vcc; AY-3-8913 and AY8930 do this internally.

                            /A9   A8    /CS   BDIR  BC2   BC1
                AY-3-8910   24    25    n/a   27    28    29
                AY-3-8912   n/a   17    n/a   18    19    20
                AY-3-8913   22    23    24    2     n/a   3
                            ------------------------------------
                Inactive            NACT      0     0     0
                Latch address       ADAR      0     0     1
                Inactive            IAB       0     1     0
                Read from Psg       DTB       0     1     1
                Latch address       BAR       1     0     0
                Inactive            DW        1     0     1
                Write to Psg        DWS       1     1     0
                Latch address       INTAK     1     1     1

*/
    /*
    AY-3-8910(A)/8914/8916/8917/8930/YM2149 (others?):
                                    _______    _______
                                  _|       \__/       |_
               [4] VSS (GND) --  |_|1  *            40|_|  -- VCC (+5v)
                                  _|                  |_
                         [5] NC  |_|2               39|_|  <- TEST 1 [1]
                                  _|                  |_
    ANALOG CHANNEL B <-  |_|3               38|_|  -> ANALOG CHANNEL C
                                  _|                  |_
    ANALOG CHANNEL A <-  |_|4               37|_|  <> DA0
                                  _|                  |_
                         [5] NC  |_|5               36|_|  <> DA1
                                  _|                  |_
                        IOB7 <>  |_|6               35|_|  <> DA2
                                  _|                  |_
                        IOB6 <>  |_|7               34|_|  <> DA3
                                  _|   /---\          |_
                        IOB5 <>  |_|8  \-/ |   A    33|_|  <> DA4
                                  _|   .   .   Y      |_
                        IOB4 <>  |_|9  |---|   - S  32|_|  <> DA5
                                  _|   '   '   3 O    |_
                        IOB3 <>  |_|10   8     - U  31|_|  <> DA6
                                  _|     3     8 N    |_
                        IOB2 <>  |_|11   0     9 D  30|_|  <> DA7
                                  _|     8     1      |_
                        IOB1 <>  |_|12         0    29|_|  <- BC1
                                  _|     P            |_
                        IOB0 <>  |_|13              28|_|  <- BC2
                                  _|                  |_
                        IOA7 <>  |_|14              27|_|  <- BDIR
                                  _|                  |_                     Prelim. DS:   YM2149/8930:
                        IOA6 <>  |_|15              26|_|  <- TEST 2 [2,3]   CS2           /SEL
                                  _|                  |_
                        IOA5 <>  |_|16              25|_|  <- A8 [3]         CS1
                                  _|                  |_
                        IOA4 <>  |_|17              24|_|  <- /A9 [3]        /CS0
                                  _|                  |_
                        IOA3 <>  |_|18              23|_|  <- /RESET
                                  _|                  |_
                        IOA2 <>  |_|19              22|_|  == CLOCK
                                  _|                  |_
                        IOA1 <>  |_|20              21|_|  <> IOA0
                                   |__________________|

    [1] Based on the decap, TEST 1 connects to the Envelope Generator and/or the
                frequency divider somehow. Is this an input or an output?
    [2] The TEST 2 input connects to the same selector as A8 and /A9 do on the 8910
                and acts as just another active high enable like A8(pin 25).
                The preliminary datasheet calls this pin CS2.
                On the 8914, it performs the same above function but additionally ?disables?
                the DA0-7 bus if pulled low/active. This additional function was removed
                on the 8910.
                This pin has an internal pullup.
                On the AY8930 and YM2149, this pin is /SEL; if low, clock input is halved.
    [3] These 3 pins are technically enables, and have pullups/pulldowns such that
                if the pins are left floating, the chip remains enabled.
    [4] On the AY-3-8910 the bond wire for the VSS pin goes to the substrate frame,
                and then a separate bond wire connects it to a pad between pins 21 and 22.
    [5] These pins lack internal bond wires entirely.


    AY-3-8912(A):
                                    _______    _______
                                  _|       \__/       |_
    ANALOG CHANNEL C <-  |_|1  *            28|_|  <> DA0
                                  _|                  |_
                      TEST 1 ->  |_|2               27|_|  <> DA1
                                  _|                  |_
                   VCC (+5V) --  |_|3               26|_|  <> DA2
                                  _|                  |_
    ANALOG CHANNEL B <-  |_|4               25|_|  <> DA3
                                  _|    /---\         |_
    ANALOG CHANNEL A <-  |_|5   \-/ |   A   24|_|  <> DA4
                                  _|    .   .   Y     |_
                   VSS (GND) --  |_|6   |---|   - S 23|_|  <> DA5
                                  _|    '   '   3 O   |_
                        IOA7 <>  |_|7    T 8    - U 22|_|  <> DA6
                                  _|     A 3    8 N   |_
                        IOA6 <>  |_|8    I 1    9 D 21|_|  <> DA7
                                  _|     W 1    1     |_
                        IOA5 <>  |_|9    A  C   2   20|_|  <- BC1
                                  _|     N  D         |_
                        IOA4 <>  |_|10      A       19|_|  <- BC2
                                  _|                  |_
                        IOA3 <>  |_|11              18|_|  <- BDIR
                                  _|                  |_
                        IOA2 <>  |_|12              17|_|  <- A8
                                  _|                  |_
                        IOA1 <>  |_|13              16|_|  <- /RESET
                                  _|                  |_
                        IOA0 <>  |_|14              15|_|  == CLOCK
                                   |__________________|


    AY-3-8913:
                                    _______    _______
                                  _|       \__/       |_
               [1] VSS (GND) --  |_|1  *            24|_|  <- /CHIP SELECT [2]
                                  _|                  |_
                        BDIR ->  |_|2               23|_|  <- A8
                                  _|                  |_
                         BC1 ->  |_|3               22|_|  <- /A9
                                  _|    /---\         |_
                         DA7 <>  |_|4   \-/ |   A   21|_|  <- /RESET
                                  _|    .   .   Y     |_
                         DA6 <>  |_|5   |---|   -   20|_|  == CLOCK
                                  _|    '   '   3     |_
                         DA5 <>  |_|6    T 8    -   19|_|  -- VSS (GND) [1]
                                  _|     A 3    8     |_
                         DA4 <>  |_|7    I 3    9   18|_|  -> ANALOG CHANNEL C
                                  _|     W 2    1     |_
                         DA3 <>  |_|8    A      3   17|_|  -> ANALOG CHANNEL A
                                  _|     N C          |_
                         DA2 <>  |_|9      -        16|_|  NC(?)
                                  _|       A          |_
                         DA1 <>  |_|10              15|_|  -> ANALOG CHANNEL B
                                  _|                  |_
                         DA0 <>  |_|11              14|_|  ?? TEST IN [3]
                                  _|                  |_
                [4] TEST OUT ??  |_|12              13|_|  -- VCC (+5V)
                                   |__________________|

    [1] Both of these are ground, they are probably connected together internally. Grounding either one should work.
    [2] This is effectively another enable, much like TEST 2 is on the AY-3-8910 and 8914, but active low
    [3] This is claimed to be equivalent to TEST 1 on the datasheet
    [4] This is claimed to be equivalent to TEST 2 on the datasheet


    GI AY-3-8910/A Programmable Sound Generator (Psg): 2 I/O ports
              A7 thru A4 enable state for selecting a register can be changed with a
                factory mask adjustment but default was 0000 for the "common" part shipped
                (probably die "-100").
              Pins 24, 25, and 26 are /A9, A8, and TEST2, which are an active low, high
                and high chip enable, respectively.
              AY-3-8910:  Unused bits in registers have unknown behavior.
              AY-3-8910A: Unused bits in registers have unknown behavior.
              I/O current source/sink behavior is unknown.
              AY-3-8910 die is labeled "90-32033" with a 1979 copyright and a "-100" die
                code.
              AY-3-8910A die is labeled "90-32128" with a 1983 copyright.
    GI AY-3-8912/A: 1 I/O port
              /A9 pin doesn't exist and is considered pulled low.
              TEST2 pin doesn't exist and is considered pulled high.
              IOB pins do not exist and have unknown behavior if driven high/low and read
                back.
              A7 thru A4 enable state for selecting a register can be changed with a
                factory mask adjustment but default was 0000 for the "common" part shipped
              AY-3-8912:  Unused bits in registers have unknown behavior.
              AY-3-8912A: Unused bits in registers have unknown behavior.
              I/O current source/sink behavior is unknown.
              AY-3-8912 die is unknown.
              AY-3-8912A or A/P die is unknown.
    AY-3-8913: 0 I/O ports
              BC2 pin doesn't exist and is considered pulled high.
              IOA/B pins do not exist and have unknown behavior if driven high/low and read back.
              A7 thru A4 enable state for selecting a register can be changed with a
                factory mask adjustment but default was 0000 for the "common" part shipped
              AY-3-8913:  Unused bits in registers have unknown behavior.
              AY-3-8913 die is unknown.
    GI AY-3-8914/A: 2 I/O ports
              A7 thru A4 enable state for selecting a register can be changed with a
                factory mask adjustment but was 0000 for the part shipped with the
                Intellivision.
              Pins 24, 25, and 26 are /A9, A8, and TEST2, which are an active low, high
                and high chip enable, respectively.
              TEST2 additionally ?disables? the data bus if pulled low.
              The register mapping is different from the AY-3-8910, the AY-3-8914 register
                mapping matches the "preliminary" 1978 AY-3-8910 datasheet.
              The Envelope/Volume control register is 6 bits wide instead of 5 bits, and
                the additional bit combines with the MPcm bit to form a bit Pair C0 and C1,
                which shift the volume output of the Envelope generator right by 0, 1 or 2
                bits on a given channel, or allow the low 4 bits to drive the channel
                volume.
              AY-3-8914:  Unused bits in registers have unknown behavior.
              AY-3-8914A: Unused bits in registers have unknown behavior.
              I/O current source/sink behavior is unknown.
              AY-3-8914 die is labeled "90-32022" with a 1978 copyright.
              AY-3-8914A die is unknown.
    GI AY-3-8916: 2 I/O ports
              A7 thru A4 enable state for selecting a register can be changed with a
                factory mask adjustment; its mask is unknown. This chip was shipped
                with certain later Intellivision II systems.
              Pins 24, 25, and 26 are /A9, /A8(!), and TEST2, which are an active low,
                low(!) and high chip enable, respectively.
                NOTE: the /A8 enable polarity may be mixed up with AY-3-8917 below.
              AY-3-8916: Unused bits in registers have unknown behavior.
              I/O current source/sink behavior is unknown.
              AY-3-8916 die is unknown.
    GI AY-3-8917: 2 I/O ports
              A7 thru A4 enable state for selecting a register can be changed with a
                factory mask adjustment but was 1111 for the part shipped with the
                Intellivision ECS module.
              Pins 24, 25, and 26 are /A9, A8, and TEST2, which are an active low, high
                and high chip enable, respectively.
                NOTE: the A8 enable polarity may be mixed up with AY-3-8916 above.
              AY-3-8917: Unused bits in registers have unknown behavior.
              I/O current source/sink behavior is unknown.
              AY-3-8917 die is unknown.
    Microchip AY8930 Enhanced Programmable Sound Generator (EPSG): 2 I/O ports
              BC2 pin exists but is always considered pulled high. The pin might have no
                bond wire at all.
              Pins 2 and 5 might be additional test pins rather than being NC.
              A7 thru A4 enable state for selecting a register are 0000 for all? parts
                shipped.
              Pins 24 and 25 are /A9, A8 which are an active low and high chip enable.
              Pin 26 is /SELECT which if driven low divides the input clock by 2.
              Writing 0xAn or 0xBn to register 0x0D turns on extended mode, which enables
                an additional 16 registers (banked using 0x0D bit 0), and clears the
                contents of all of the registers except the high 3 bits of register 0x0D
                (according to the datasheet).
              If the AY8930's extended mode is enabled, it gains higher resolution
                frequency and volume control, separate volume per-channel, and the duty
                cycle can be adjusted for the 3 channels.
              If the mode is not enabled, it behaves almost exactly like an AY-3-8910(A?),
                barring the BC2 and /SELECT differences.
              AY8930: Unused bits in registers have unknown behavior, but the datasheet
                explicitly states that unused bits always read as 0.
              I/O current source/sink behavior is unknown.
              AY8930 die is unknown.
    Yamaha YM2149 Software-Controlled Sound Generator (SSG): 2 I/O ports
              A7 thru A4 enable state for selecting a register are 0000 for all? parts
                shipped.
              Pins 24 and 25 are /A9, A8 which are an active low and high chip enable.
              Pin 26 is /SEL which if driven low divides the input clock by 2.
              The YM2149 envelope register has 5 bits of resolution internally, allowing
              for smoother volume ramping, though the register for setting its direct
              value remains 4 bits wide.
              YM2149: Unused bits in registers have unknown behavior.
              I/O current source/sink behavior is unknown.
              YM2149 die is unknown; only one die revision, 'G', has been observed
                from Yamaha chip/datecode silkscreen surface markings.
    Yamaha YM2203: 2 I/O ports
              The pinout of this chip is completely different from the AY-3-8910.
              The entire way this chip is accessed is completely different from the usual
                AY-3-8910 selection of chips, so there is a /CS and a /RD and a /WR and
                an A0 pin; The chip status can be read back by reading the register
                select address.
              The chip has a 3-channel, 4-op FM synthesis Sound core in it, not discussed
                in this source file.
              The first 16 registers are the same(?) as the YM2149.
              YM2203: Unused bits in registers have unknown behavior.
              I/O current source/sink behavior is unknown.
              YM2203 die is unknown; three die revisions, 'D', 'F' and 'H', have been
                observed from Yamaha chip/datecode silkscreen surface markings. It is
                unknown what behavioral differences exist between these revisions.
                The 'D' revision only appears during the first year of production, 1984, on chips marked 'YM2203B'
                The 'F' revision exists from 1984?-1991, chips are marked 'YM2203C'
                The 'H' revision exists from 1991 onward, chips are marked 'YM2203C'
    Yamaha YM3439: limited info: CMOS version of YM2149?
    Yamaha YMZ284: limited info: 0 I/O port, different clock divider
              The chip selection logic is again simplified here: pin 1 is /WR, pin 2 is
                /CS and pin 3 is A0.
              D0-D7 are conveniently all on one side of the 16-pin package.
              Pin 8 is /IC (initial clear), with an internal pullup.
    Yamaha YMZ294: limited info: 0 I/O port
              Pinout is identical to YMZ284 except for two additions: pin 8 selects
                between 4MHz (H) and 6MHz (L), while pin 10 is /TEST.
    OKI M5255, Winbond WF19054, JFC 95101, File KC89C72, Toshiba T7766A : differences to be listed

    Decaps:
    AY-3-8914 - http://siliconpr0n.org/map/gi/ay-3-8914/mz_mit20x/
    AY-3-8910 - http://privatfrickler.de/blick-auf-den-chip-soundchip-general-instruments-ay-3-8910/
    AY-3-8910A - https://seanriddledecap.blogspot.com/2017/01/gi-ay-3-8910-ay-3-8910a-gi-8705-cba.html (TODO: update this link when it has its own page at seanriddle.com)

    Links:
    AY-3-8910 'preliminary' datasheet (which actually describes the AY-3-8914) from 1978:
              http://spatula-city.org/~im14u2c/intv/gi_micro_programmable_tv_games/page_7_100.png
              http://spatula-city.org/~im14u2c/intv/gi_micro_programmable_tv_games/page_7_101.png
              http://spatula-city.org/~im14u2c/intv/gi_micro_programmable_tv_games/page_7_102.png
              http://spatula-city.org/~im14u2c/intv/gi_micro_programmable_tv_games/page_7_103.png
              http://spatula-city.org/~im14u2c/intv/gi_micro_programmable_tv_games/page_7_104.png
              http://spatula-city.org/~im14u2c/intv/gi_micro_programmable_tv_games/page_7_105.png
    AY-3-8910/8912 Feb 1979 manual: http://dev-docs.atariforge.org/files/GI_AY-3-8910_Feb-1979.pdf
    AY-3-8910/8912/8913 post-1983 manual: http://map.grauw.nl/resources/sound/generalinstrument_ay-3-8910.pdf or http://www.ym2149.com/ay8910.pdf
    AY-8930 datasheet: http://www.ym2149.com/ay8930.pdf
    YM2149 datasheet: http://www.ym2149.com/ym2149.pdf
    YM2203 English datasheet: http://www.appleii-box.de/APPLE2/JonasCard/YM2203%20datasheet.pdf
    YM2203 Japanese datasheet contents, translated: http://www.larwe.com/technical/chip_ym2203.html
    */
    private static class Ay8910Context {

        static Logger log = Logger.getLogger(Ay8910Context.class.getName());

        // cfg.chipType: chip type
        //    AY8910 variants
        private enum AyType {
            AY8910(0x00), AY8912(0x01), AY8913(0x02), AY8930(0x03), AY8914(0x04),
            //YM2149 variants
            YM2149(0x10), YM3439(0x11), YMZ284(0x12), YMZ294(0x13),
            //OPN/OPNA SSG
            YM2203(0x20), YM2608(0x21), YM2610(0x22);
            final int v;

            AyType(int v) {
                this.v = v;
            }

            static AyType valueOf(int v) {
                return Arrays.stream(values()).filter(e -> e.v == v).findFirst().get();
            }
        }

        // cfg.chipFlags: pin26 state
        private static final int YM2149_PIN26_HIGH = 0x00;
        private static final int YM2149_PIN26_LOW = 0x10; // additional clock divider /2

        private static final int AY8910_ZX_STEREO = 0x80;

        private static final int DEVCB_TYPE_NULL = (0);
        private static final int[] DEVCB_NULL = new int[] {DEVCB_TYPE_NULL};

        /*
        AY-3-8910A: 2 I/O ports
        AY-3-8912A: 1 I/O port
        AY-3-8913A: 0 I/O port
        AY8930: upper compatible with 8910.
        In extended mode, it has higher resolution and duty ratio setting
        YM2149: higher resolution
        YM3439: same as 2149
        YMZ284: 0 I/O port, different clock divider
        YMZ294: 0 I/O port
        */

        private static final int ALL_8910_CHANNELS = -1;

        /* Internal resistance at Volume level 7. */

        private static final int AY8910_INTERNAL_RESISTANCE = 356;
        private static final int YM2149_INTERNAL_RESISTANCE = 353;

        /*
         * Default values for resistor loads.
         * The macro should be used in AY8910interface if
         * the real values are unknown.
         */
        private static final int[] AY8910_DEFAULT_LOADS = new int[] {1000, 1000, 1000};

        /*
         * The following is used by all drivers not reviewed yet.
         * This will like the old behaviour, output between
         * 0 and 7FFF
         */
        private static final int AY8910_LEGACY_OUTPUT = 1;

        /*
         * Specifing the next define will simulate the special
         * cross channel mixing if outputs are tied together.
         * The driver will only provide one stream in this case.
         */
        private static final int AY8910_SINGLE_OUTPUT = 2;

        /*
         * The follwoing define is the default behaviour.
         * Output level 0 is 0V and 7ffff corresponds to 5V.
         * Use this to specify that a discrete mixing stage
         * follows.
         */
        private static final int AY8910_DISCRETE_OUTPUT = 4;

        /*
         * The follwoing define causes the driver to output
         * raw volume levels, i.e. 0 .. 15 and 0..31.
         * This is intended to be used in a subsequent
         * mixing modul (i.e. mpatrol ties 6 channels from
         * AY-3-8910 together). Do not use it now.
         */
        /* TODO: implement mixing module */
        private static final int AY8910_RAW_OUTPUT = 8;

        //private static final int AY8910_ZX_STEREO = 0x80;
        /*
         * This define specifies the initial state of YM2149
         * pin 26 (SEL pin). By default it is set to high,
         * compatible with AY8910.
         */
        /* TODO: make it controllable while it's running (used by any hw???) */
        //private static final int YM2149_PIN26_HIGH = (0x00); /* or N/C */
        //private static final int YM2149_PIN26_LOW = (0x10);

        private static class Ay8910Interface {
            public int flags; // Flags */
            public int[] resLoad = new int[3]; // Load on channel in ohms */
            //devcb_read8            portAread;
            //devcb_read8            portBread;
            //devcb_write8        portAwrite;
            //devcb_write8        portBwrite;
        }

        /*
         *  Defines
         */

        private static final int ENABLE_REGISTER_TEST = 0; // Enable preprogrammed registers */
        private static final int LOG_IGNORED_WRITES = 0;
        private static final int ENABLE_CUSTOM_OUTPUTS = 0;

        private static final int MAX_OUTPUT = 0x4000;
        private static final int NUM_CHANNELS = 3;

        /* register id's */
        private static final int AY_AFINE = 0;
        private static final int AY_ACOARSE = 1;
        private static final int AY_BFINE = 2;
        private static final int AY_BCOARSE = 3;
        private static final int AY_CFINE = 4;
        private static final int AY_CCOARSE = 5;
        private static final int AY_NOISEPER = 6;
        private static final int AY_ENABLE = 7;
        private static final int AY_AVOL = 8;
        private static final int AY_BVOL = 9;
        private static final int AY_CVOL = 10;
        private static final int AY_EFINE = 11;
        private static final int AY_ECOARSE = 12;
        private static final int AY_ESHAPE = 13;

        private static final int AY_PORTA = 14;
        private static final int AY_PORTB = 15;

        /*
         *  Type definitions
         */

        private enum PsgType {
            PSG_TYPE_AY,
            PSG_TYPE_YM
        }

        private static class AyYmParam {
            public double rUp;
            public double rDown;
            public int resCount;
            public double[] res = new double[32];
        }

        private static class MosfetParam {
            public double vth;
            public double vg;
            public int count;
            public double[] kn = new double[32];
        }

        // internal state
        public PsgType type;
        public byte streams;
        public byte ioports;
        //(int)8 ready;
        public byte active;
        public byte register_latch;
        public byte[] regs = new byte[16];
        public byte lastEnable;
        public int[] count = new int[NUM_CHANNELS];
        public byte[] output = new byte[NUM_CHANNELS];
        public byte preScaleNoise;
        public int countNoise;
        public int countEnv;
        public byte envStep;
        public int envVolume;
        public byte hold, alternate, attack, holding;
        public int rng;
        public byte envStepMask;
        /* init parameters ... */
        public int step;
        public byte zeroIsOff;
        public byte[] volEnabled = new byte[NUM_CHANNELS];
        public AyYmParam par;
        public AyYmParam parEnv;
        public int[][] volTable = new int[][] {
                new int[16], new int[16], new int[16]
        };
        public int[][] envTable = new int[][] {
                new int[32], new int[32], new int[32]
        };
        // #if ENABLE_CUSTOM_OUTPUTS
        int[] vol3DTable = new int[8 * 32 * 32 * 32];
        // #endif
        public int flags; // Flags */
        public int[] resLoad = new int[3]; // Load on channel in ohms */
        //devcb_read8 port_a_read_cb;
        //devcb_read8 port_b_read_cb;
        //devcb_write8 port_a_write_cb;
        //devcb_write8 port_b_write_cb;

        public byte[] stereoMask = new byte[NUM_CHANNELS];
        public int[] muteMask = new int[NUM_CHANNELS];

        public int clock;
        public byte chipType;
        public byte chipFlags;

        public DEVCB_SRATE_CHG smpRateFunc;
        public Object smpRateData;

        private int getNoiseEnabled(int _chan) {
            return (this.regs[AY_ENABLE] >> (3 + _chan)) & 1;
        }

        private int getToneEnabled(int _chan) {
            return (this.regs[AY_ENABLE] >> _chan) & 1;
        }

        private int getTonePeriod(int _chan) {
            return this.regs[_chan << 1] | ((this.regs[(_chan << 1) | 1] & 0x0f) << 8);
        }

        private int getNoisePeriod() {
            return this.regs[AY_NOISEPER] & 0x1f;
        }

        private int getToneVolume(int _chan) {
            return this.regs[AY_AVOL + _chan] & 0x0f;
        }

        private int getToneEnvelope(int _chan) {
            return (this.regs[AY_AVOL + _chan] >> 4) & ((this.chipType == (byte) AyType.AY8914.v) ? 3 : 1);
        }

        private int getEnvelopePeriod() {
            return this.regs[AY_EFINE] | (this.regs[AY_ECOARSE] << 8);
        }

        private int getNoiseOutput() {
            return this.rng & 1;
        }

        private void writeReg(byte r, byte v) {
            //if (r >= 11 && r <= 13 ) printf("%d %x %02x\n", this.index, r, v);
            this.regs[r] = v;

            switch (r) {
            case AY_AFINE:
            case AY_ACOARSE:
            case AY_BFINE:
            case AY_BCOARSE:
            case AY_CFINE:
            case AY_CCOARSE:
            case AY_NOISEPER:
            case AY_AVOL:
            case AY_BVOL:
            case AY_CVOL:
            case AY_EFINE:
            case AY_ECOARSE:
                /* No action required */
                break;
            case AY_ENABLE:
                if (this.lastEnable == 0xFF)
                    this.lastEnable = (byte) ~this.regs[AY_ENABLE];

                if ((this.lastEnable & 0x40) != (this.regs[AY_ENABLE] & 0x40)) {
                    /* write out 0xff if port set to input */
                    //if (this.port_a_write_cb != NULL)
                    //    this.port_a_write_cb(psg, 0, (this.regs[AY_ENABLE] & 0x40) ? this.regs[AY_PORTA] : 0xff);
                }

                if ((this.lastEnable & 0x80) != (this.regs[AY_ENABLE] & 0x80)) {
                    /* write out 0xff if port set to input */
                    //if (this.port_b_write_cb != NULL)
                    //    this.port_b_write_cb(psg, 0, (this.regs[AY_ENABLE] & 0x80) ? this.regs[AY_PORTB] : 0xff);
                }

                this.lastEnable = (byte) (this.regs[AY_ENABLE] & 0xC0);
                break;
            case AY_ESHAPE:
                this.attack = (this.regs[AY_ESHAPE] & 0x04) != 0 ? this.envStepMask : (byte) 0x00;
                if ((this.regs[AY_ESHAPE] & 0x08) == 0) {
                    /* if Continue = 0, map the shape to the equivalent one which has Continue = 1 */
                    this.hold = 1;
                    this.alternate = this.attack;
                } else {
                    this.hold = (byte) (this.regs[AY_ESHAPE] & 0x01);
                    this.alternate = (byte) (this.regs[AY_ESHAPE] & 0x02);
                }
                this.envStep = this.envStepMask;
                this.holding = 0;
                this.envVolume = this.envStep ^ this.attack;
                break;
            case AY_PORTA:
                if ((this.regs[AY_ENABLE] & 0x40) != 0) {
                    //if (this.port_a_write_cb != NULL)
                    //    this.port_a_write_cb(psg, 0, this.regs[AY_PORTA]);
                    //else
                    //    Debug.printf("warning: unmapped write %02x to %s Port A\n", v, "AY8910");
                } else {
// #if LOG_IGNORED_WRITES
                    log.warning(String.format("write %02x to %s Port A set as input - ignored\n", v, "AY8910"));
// #endif
                }
                break;
            case AY_PORTB:
                if ((this.regs[AY_ENABLE] & 0x80) != 0) {
                    //if (this.port_b_write_cb != NULL)
                    //    this.port_b_write_cb(psg, 0, this.regs[AY_PORTB]);
                    //else
                    //    Debug.printf("warning: unmapped write %02x to %s Port B\n", v, "AY8910");
                } else {
// #if LOG_IGNORED_WRITES
                    log.warning(String.format("write %02x to %s Port B set as input - ignored\n", v, "AY8910"));
// #endif
                }
                break;
            }
        }

        private void updateOne(int samples, int[][] outputs) {
            int[] bufL = outputs[0];
            int[] bufR = outputs[1];

            for (int i = 0; i < samples; i++) {
                outputs[0][i] = 0;
                outputs[1][i] = 0;
            }

            /* The 8910 has three outputs, each output is the mix of one of the three */
            /* tone generators and of the (single) noise generator. The two are mixed */
            /* BEFORE going into the DAC. The formula to mix each channel is: */
            /* (ToneOn | ToneDisable) & (NoiseOn | NoiseDisable). */
            /* Note that this means that if both tone and noise are disabled, the output */
            /* is 1, not 0, and can be modulated changing the volume. */

            /* buffering loop */
            for (int curSmpl = 0; curSmpl < samples; curSmpl++) {
                for (int chan = 0; chan < NUM_CHANNELS; chan++) {
                    this.count[chan]++;
                    if (this.count[chan] >= getTonePeriod(chan)) {
                        this.output[chan] ^= 1;
                        this.count[chan] = 0;
                    }
                }

                this.countNoise++;
                if (this.countNoise >= getNoisePeriod()) {
                    /* toggle the prescaler output. Noise is no different to
                     * channels.
                     */
                    this.countNoise = 0;
                    this.preScaleNoise ^= 1;

                    if (this.preScaleNoise != 0) {
                        /* The Random Number Generator of the 8910 is a 17-bit shift */
                        /* register. The input to the shift register is bit0 XOR bit3 */
                        /* (bit0 is the output). This was verified on AY-3-8910 and YM2149 chips. */

                        this.rng ^= (((this.rng & 1) ^ ((this.rng >> 3) & 1)) << 17);
                        this.rng >>= 1;
                    }
                }

                for (int chan = 0; chan < NUM_CHANNELS; chan++) {
                    this.volEnabled[chan] = (byte) (
                            (this.output[chan] | getToneEnabled(chan))
                                    & (getNoiseOutput() | getNoiseEnabled(chan))
                    );
                }

                /* update envelope */
                if (this.holding == 0) {
                    this.countEnv++;
                    if (this.countEnv >= getEnvelopePeriod() * this.step) {
                        this.countEnv = 0;
                        this.envStep--;

                        /* check envelope current position */
                        if (this.envStep < 0) {
                            if (this.hold != 0) {
                                if (this.alternate != 0)
                                    this.attack ^= this.envStepMask;
                                this.holding = 1;
                                this.envStep = 0;
                            } else {
                                /* if CountEnv has looped an odd number of times (usually 1), */
                                /* invert the output. */
                                if (this.alternate != 0 && (this.envStep & (this.envStepMask + 1)) != 0)
                                    this.attack ^= this.envStepMask;

                                this.envStep &= this.envStepMask;
                            }
                        }

                    }
                }
                this.envVolume = this.envStep ^ this.attack;

// #if ENABLE_CUSTOM_OUTPUTS
                if (this.streams == 3) {
// #endif
                    int chnOut;
                    for (int chan = 0; chan < NUM_CHANNELS; chan++) {
                        if (this.muteMask[chan] == 0)
                            continue;
                        if (getToneEnvelope(chan) != 0) {
                            if (this.chipType == (byte) AyType.AY8914.v) { // AY8914 Has a two bit tone_envelope field
                                chnOut = this.envTable[chan][this.volEnabled[chan] != 0 ? this.envVolume >> (3 - getToneEnvelope(chan)) : 0];
                            } else {
                                chnOut = this.envTable[chan][this.volEnabled[chan] != 0 ? this.envVolume : 0];
                            }
                        } else {
                            chnOut = this.volTable[chan][this.volEnabled[chan] != 0 ? getToneVolume(chan) : 0];
                        }
                        if ((this.stereoMask[chan] & 0x01) != 0)
                            bufL[curSmpl] += chnOut;
                        if ((this.stereoMask[chan] & 0x02) != 0)
                            bufR[curSmpl] += chnOut;
                    }
// #if ENABLE_CUSTOM_OUTPUTS
                } else {
                    int chnOut = mix3D();
                    bufL[curSmpl] += chnOut;
                    bufR[curSmpl] += chnOut;
                }
// #endif
            }
        }

        private void buildMixerTable() {
// #if ENABLE_CUSTOM_OUTPUTS
//    int normalize = 0;
//    int chan;
//
//    if ((this.flags & AY8910_LEGACY_OUTPUT) != 0 || this.flags == 0)
//    {
//        //Debug.printf("AY-3-8910/YM2149 using legacy output levels!\n");
//        normalize = 1;
//    }
//
//    if ((this.flags & AY8910_RESISTOR_OUTPUT) != 0)
//    {
//        if (this.type != PSG_TYPE_AY)
//            log.severe("AY8910_RESISTOR_OUTPUT currently only supported for AY8910 devices.");
//
//        for (chan = 0; chan < NUM_CHANNELS; chan++)
//        {
//            build_mosfet_resistor_table(ay8910_mosfet_param, this.res_load[chan], this.vol_table[chan]);
//            build_mosfet_resistor_table(ay8910_mosfet_param, this.res_load[chan], this.env_table[chan]);
//        }
//    }
//    else if (this.streams == NUM_CHANNELS)
//    {
//        for (chan = 0; chan < NUM_CHANNELS; chan++)
//        {
//            build_single_table(this.res_load[chan], this.par, normalize, this.vol_table[chan], this.zero_is_off);
//            build_single_table(this.res_load[chan], this.par_env, normalize, this.env_table[chan], 0);
//        }
//    }
            /*
             * The previous implementation added all three channels up instead of averaging them.
             * The factor of 3 will force the same levels if normalizing is used.
             */
//    else
//    {
//        build_3D_table(this.res_load[0], this.par, this.par_env, normalize, 3, this.zero_is_off, this.vol3d_table);
//    }
// #else

            // resistor output just doesn't work here
            // and the 3D table takes an eternity to initialize (and I don't have muting implemented - and panning is harder)
            for (int chan = 0; chan < NUM_CHANNELS; chan++) {
                buildSingleTable(this.resLoad[chan], this.par, 1, this.volTable[chan], this.zeroIsOff);
                buildSingleTable(this.resLoad[chan], this.parEnv, 1, this.envTable[chan], 0);
            }
// #endif
        }

        /*
         * Public functions
         *
         *   used by e.g. YM2203, YM2210 ...
         */

        private void setType(byte chipType) {
            this.chipType = chipType;
            if ((chipType & 0xF0) == 0x20)
                chipType = (byte) AyType.YM2149.v;

            switch (AyType.valueOf(chipType)) {
            case AY8910:
            case AY8914:
            case AY8930:
                this.type = PsgType.PSG_TYPE_AY;
                this.streams = 3;
                this.ioports = 2;
                break;
            case AY8912:
                this.type = PsgType.PSG_TYPE_AY;
                this.streams = 3;
                this.ioports = 1;
                break;
            case AY8913:
                this.type = PsgType.PSG_TYPE_AY;
                this.streams = 3;
                this.ioports = 0;
                break;
            case YM2149:
            case YM3439:
                this.type = PsgType.PSG_TYPE_YM;
                this.streams = 3;
                this.ioports = 2;
                break;
            case YMZ284:
            case YMZ294:
                this.type = PsgType.PSG_TYPE_YM;
                this.streams = 1;
                this.ioports = 0;
                break;
            default:
                this.type = ((chipType & 0xF0) == 0x00) ? PsgType.PSG_TYPE_AY : PsgType.PSG_TYPE_YM;
                this.streams = 3;
                this.ioports = 2;
                break;
            }

            if (this.type == PsgType.PSG_TYPE_AY) { // AYTYPE_AY89xx variants
                this.step = 2;
                this.par = ay8910_param;
                this.parEnv = ay8910_param;
                this.zeroIsOff = 1;
                this.envStepMask = 0x0f;
            } else { //if (this.type = PSG_TYPE_YM) // AYTYPE_YMxxxx variants (also YM2203/2608/2610 SSG)
                this.step = 1;
                this.par = ym2149Param;
                this.parEnv = ym2149_param_env;
                this.zeroIsOff = 0;
                this.envStepMask = 0x1f;
            }
        }

        //private byte device_start_ay8910_mame(DEV_INFO retDevInf) {
        //    Ay8910Context chip;
        //    DEV_DATA devData;
        //    int rate;

        //    rate = ay8910_start(chip, cfg._genCfg.clock, cfg.chipType, cfg.chipFlags);
        //    if (chip == null)
        //        return 0xFF;

        //    devData = (DEV_DATA)chip;
        //    devData.chipInf = chip;
        //    INIT_DEVINF(retDevInf, devData, rate, devDef_AY8910_MAME);
        //    return 0x00;
        //}

        private int init(int clock, byte ay_type, byte ay_flags) {

            this.smpRateFunc = null;

            this.clock = clock;
            this.chipFlags = ay_flags;
            this.setType(ay_type);
            this.resLoad[0] = this.resLoad[1] = this.resLoad[2] = 1000; //Default values for resistor loads

            if ((this.chipFlags & AY8910_ZX_STEREO) != 0) {
                // ABC Stereo
                this.stereoMask[0] = 0x01;
                this.stereoMask[1] = 0x03;
                this.stereoMask[2] = 0x02;
            } else {
                this.stereoMask[0] = 0x03;
                this.stereoMask[1] = 0x03;
                this.stereoMask[2] = 0x03;
            }

            this.buildMixerTable();

            //ay8910_set_clock(info, clock);
            setMuteMask(0x00);

            return getSampleRate();
        }

        private void stop() {
        }

        private void reset() {
            byte i;

            this.active = 0;
            this.register_latch = 0;
            this.rng = 1;
            this.output[0] = 0;
            this.output[1] = 0;
            this.output[2] = 0;
            this.count[0] = 0;
            this.count[1] = 0;
            this.count[2] = 0;
            this.countNoise = 0;
            this.countEnv = 0;
            this.preScaleNoise = 0;
            this.lastEnable = (byte) 0xFF; // force to write
            for (i = 0; i < AY_PORTA; i++)
                writeReg(i, (byte) 0);
            //this.ready = 1;
// #if ENABLE_REGISTER_TEST
            writeReg((byte) AY_AFINE, (byte) 0);
            writeReg((byte) AY_ACOARSE, (byte) 1);
            writeReg((byte) AY_BFINE, (byte) 0);
            writeReg((byte) AY_BCOARSE, (byte) 2);
            writeReg((byte) AY_CFINE, (byte) 0);
            writeReg((byte) AY_CCOARSE, (byte) 4);
            //#define AY_NOISEPER   (6)
            writeReg((byte) AY_ENABLE, (byte) ~7);
            writeReg((byte) AY_AVOL, (byte) 10);
            writeReg((byte) AY_BVOL, (byte) 10);
            writeReg((byte) AY_CVOL, (byte) 10);
            //#define AY_EFINE  (11)
            //#define AY_ECOARSE    (12)
            //#define AY_ESHAPE (13)
// #endif
        }

        private void setClock(int clock) {
            this.clock = clock;
            if (this.smpRateFunc != null)
                this.smpRateFunc.accept(this.smpRateData, getSampleRate());
        }

        private int getSampleRate() {
            int master_clock = this.clock;

            if (this.type == PsgType.PSG_TYPE_YM) {
                // YM2149 master clock divider
                if ((this.chipFlags & YM2149_PIN26_LOW) != 0)
                    master_clock /= 2;
            }
            /* The envelope is pacing twice as fast for the YM2149 as for the AY-3-8910,    */
            /* This handled by the step parameter. Consequently, we use a divider of 8 here. */
            return master_clock / 8;
        }

        private void write(byte addr, byte data) {
            if ((addr & 1) != 0) {
                if (this.active != 0) {
                    /* Data port */
                    writeReg(this.register_latch, data);
                }
            } else {
                this.active = (byte) ((data >> 4) == 0 ? 1 : 0); // mask programmed 4-bit code
                if (this.active != 0) {
                    /* Register port */
                    this.register_latch = (byte) (data & 0x0f);
                } else {
                    Debug.printf(Level.WARNING, "%s upper address mismatch\n", "AY8910");
                }
            }
        }

        static final byte[][] AYmask = new byte[][] {
                new byte[] {
                        (byte) 0xff, 0x0f, (byte) 0xff, 0x0f, (byte) 0xff, 0x0f, 0x1f, (byte) 0xff, 0x3f, 0x3f, 0x3f, (byte) 0xff, (byte) 0xff, 0x0f, (byte) 0xff, (byte) 0xff
                },
                new byte[] {
                        (byte) 0xff, 0x0f, (byte) 0xff, 0x0f, (byte) 0xff, 0x0f, 0x1f, (byte) 0xff, 0x1f, 0x1f, 0x1f, (byte) 0xff, (byte) 0xff, 0x0f, (byte) 0xff, (byte) 0xff
                }
        };

        private byte read(byte addr) {
            byte r = this.register_latch;

            if (this.active == 0) return (byte) 0xff; // high impedance

            switch (r) {
            case AY_PORTA:
                if ((this.regs[AY_ENABLE] & 0x40) != 0)
                    Debug.printf("warning: read from %s Port A set as output\n", "AY8910");
                    /*
                       even if the port is set as output, we still need to return the external
                       data. Some games, like kidniki, need this to work.

                       FIXME: The io ports are designed as open collector outputs. Bits 7 and 8 of AY_ENABLE
                       only enable (low) or disable (high) the pull up resistors. The YM2149 datasheet
                       specifies those pull up resistors as 60k to 600k (min / max).
                       We do need a Callback for those two flags. Kid Niki (Irem m62) is one such
                       case were it makes a difference in comparison to a standard TTL output.
                     */
                //if (this.port_a_read_cb != NULL)
                //    this.regs[AY_PORTA] = this.port_a_read_cb(psg, 0);
                //else
                //    Debug.printf("Warning - read 8910 Port A\n");
                break;
            case AY_PORTB:
                if ((this.regs[AY_ENABLE] & 0x80) != 0)
                    Debug.printf("warning: read from %s Port B set as output\n", "AY8910");
                //if (this.port_b_read_cb != NULL)
                //    this.regs[AY_PORTB] = this.port_b_read_cb(psg, 0);
                //else
                //    Debug.printf("Warning - read 8910 Port B\n");
                break;
            }

            /* Depending on chip type, unused bits in registers may or may not be accessible.
            Untested chips are assumed to regard them as 'ram'
            Tested and confirmed on hardware:
            - AY-3-8910: inaccessible bits (see masks below) read back as 0
            - AY-3-8914: same as 8910 except regs B,C,D (8,9,A below due to 8910.8914 remapping) are 0x3f
            - AY-3-8916/8917 (used on ECS INTV expansion): inaccessible bits mirror one of the i/o ports, needs further testing
            - YM2149: no anomaly
            */
            if (this.chipType == (byte) AyType.AY8914.v) {
                return (byte) (this.regs[r] & AYmask[0][r]);
            }
            //else if (this.chip_type == AYTYPE_AY8910) {
            else if (this.type == PsgType.PSG_TYPE_AY) {
                return (byte) (this.regs[r] & AYmask[1][r]);
            } else return this.regs[r];
        }

        private void setMuteMask(int muteMask) {
            for (byte curChn = 0; curChn < NUM_CHANNELS; curChn++)
                this.muteMask[curChn] = (muteMask & (1 << curChn)) != 0 ? 0 : ~0;
        }

        private void setStereoMask(int stereoMask) {
            for (byte curChn = 0; curChn < NUM_CHANNELS; curChn++) {
                this.stereoMask[curChn] = (byte) (stereoMask & 0x03);
                stereoMask >>= 2;
            }
        }

        private void setSrchgCb(DEVCB_SRATE_CHG callbackFunc, Object dataPtr) {
            // set Sample Rate Change Callback routine
            smpRateFunc = callbackFunc;
            smpRateData = dataPtr;
        }

        int mix3D() {
            int indx = 0;

            for (int chan = 0; chan < NUM_CHANNELS; chan++)
                if (getToneEnvelope(chan) != 0) {
                    if (chipType == AyType.AY8914.v) // AY8914 Has a two bit tone_envelope field
                    {
                        indx |= (1 << (chan + 15)) | (volEnabled[chan] != 0 ? ((envVolume >> (3 - getToneEnvelope(chan))) << (chan * 5)) : 0);
                    } else {
                        indx |= (1 << (chan + 15)) | (volEnabled[chan] != 0 ? envVolume << (chan * 5) : 0);
                    }
                } else {
                    indx |= (volEnabled[chan] != 0 ? getToneVolume(chan) << (chan * 5) : 0);
                }
            return vol3DTable[indx];
        }

        private void buildSingleTable(double rl, AyYmParam par, int normalize, int[] tab, int zeroIsOff) {
            double[] temp = new double[32];
            double min = 10.0, max = 0.0;

            for (int j = 0; j < par.resCount; j++) {
                double rt = 1.0 / par.rDown + 1.0 / rl;

                double rw = 1.0 / par.res[j];
                rt += 1.0 / par.res[j];

                if (!(zeroIsOff != 0 && j == 0)) {
                    rw += 1.0 / par.rUp;
                    rt += 1.0 / par.rUp;
                }

                temp[j] = rw / rt;
                if (temp[j] < min)
                    min = temp[j];
                if (temp[j] > max)
                    max = temp[j];
            }
            if (normalize != 0) {
                for (int j = 0; j < par.resCount; j++)
                    /* The following line generates values that cause clicks when starting/pausing/stopping
                        because there're off (the center is at zero, not the base).
                        That's quite bad for a player.
                    */
                    tab[j] = (int) (MAX_OUTPUT * ((temp[j] - min) / (max - min)) / NUM_CHANNELS);
            } else {
                for (int j = 0; j < par.resCount; j++)
                    tab[j] = (int) (MAX_OUTPUT * temp[j] / NUM_CHANNELS);
            }
        }

        public interface DEVCB_SRATE_CHG extends BiConsumer<Object, Integer> {
        }

        /*
         *  Static
         */

        private static final AyYmParam ym2149Param = new AyYmParam() {{
            rUp = 630;
            rDown = 801;
            resCount = 16;
            res = new double[] {
                    73770, 37586, 27458, 21451, 15864, 12371, 8922, 6796,
                    4763, 3521, 2403, 1737, 1123, 762, 438, 251
            };
        }};

        private static final AyYmParam ym2149_param_env = new AyYmParam() {{
            rUp = 630;
            rDown = 801;
            resCount = 32;
            res = new double[] {103350, 73770, 52657, 37586, 32125, 27458, 24269, 21451,
                    18447, 15864, 14009, 12371, 10506, 8922, 7787, 6796,
                    5689, 4763, 4095, 3521, 2909, 2403, 2043, 1737,
                    1397, 1123, 925, 762, 578, 438, 332, 251};
        }};

// #if false
        /* RL = 1000, Hacker Kay normalized, 2.1V to 3.2V */
//static final AyYmParam ay8910_param = new AyYmParam() {{
//    r_up = 664;
//    r_down = 913;
//    res_count = 16;
//    res = new double[]{ 85785, 34227, 26986, 20398, 14886, 10588,  7810,  4856,
//       4120,  2512,  1737,  1335,  1005,   747,   586,    451 };
//}};

        /*
         * RL = 3000, Hacker Kay normalized pattern, 1.5V to 2.8V
         * These values correspond with guesses based on Gyruss schematics
         * They work well with scramble as well.
         */
//static final AyYmParam ay8910_param = {
//    930, 454,
//    16,
//    { 85066, 34179, 27027, 20603, 15046, 10724, 7922, 4935,
//       4189,  2557,  1772,  1363,  1028,  766,   602,  464 },
//};

        /*
         * RL = 1000, Hacker Kay normalized pattern, 0.75V to 2.05V
         * These values correspond with guesses based on Gyruss schematics
         * They work well with scramble as well.
         */
//static final AyYmParam ay8910_param = {
//    1371, 313,
//    16,
//    { 93399, 33289, 25808, 19285, 13940, 9846,  7237,  4493,
//       3814,  2337,  1629,  1263,   962,  727,   580,   458 },
//};

        /*
         * RL = 1000, Hacker Kay normalized pattern, 0.2V to 1.5V
         */
//static final AyYmParam ay8910_param = {
//    5806, 300,
//    16,
//    { 118996, 42698, 33105, 24770, 17925, 12678,  9331,  5807,
//        4936,  3038,  2129,  1658,  1271,   969,   781,   623 }
//};
// #endif

        /*
         * RL = 2000, Based on Matthew Westcott's measurements from Dec 2001.
         * -------------------------------------------------------------------
         *
         * http://groups.google.com/group/comp.sys.sinclair/browse_thread/thread/fb3091da4c4caf26/d5959a800cda0b5e?lnk=gst&q=Matthew+Westcott#d5959a800cda0b5e
         * After what Russell mentioned a couple of weeks back about the lack of
         * publicised measurements of AY chip volumes - I've finally got round to
         * making these readings, and I'm placing them in the public domain - so
         * anyone's welcome to use them in emulators or anything else.

         * To make the readings, I set up the chip to produce a constant voltage on
         * channel C (setting bits 2 and 5 of register 6), and varied the amplitude
         * (the low 4 bits of register 10). The voltages were measured between the
         * channel C output (pin 1) and ground (pin 6).
         *
         * Level  Voltage
         *  0     1.147
         *  1     1.162
         *  2     1.169
         *  3     1.178
         *  4     1.192
         *  5     1.213
         *  6     1.238
         *  7     1.299
         *  8     1.336
         *  9     1.457
         * 10     1.573
         * 11     1.707
         * 12     1.882
         * 13     2.06
         * 14     2.32
         * 15     2.58
         * -------------------------------------------------------------------
         *
         * The ZX spectrum output circuit was modelled in SwitcherCAD and
         * the resistor values below create the voltage levels above.
         * RD was measured on a real chip to be 8m Ohm, RU was 0.8m Ohm.
         */

        private static final AyYmParam ay8910_param = new AyYmParam() {{
            rUp = 800000;
            rDown = 8000000;
            resCount = 16;
            res = new double[] {
                    15950, 15350, 15090, 14760, 14275, 13620, 12890, 11370,
                    10600, 8590, 7190, 5985, 4820, 3945, 3017, 2345
            };
        }};

        private static final MosfetParam ay8910_mosfet_param = new MosfetParam() {{
            vth = 1.465385778;
            vg = 4.9;
            count = 16;
            kn = new double[] {
                    0.00076,
                    0.80536,
                    1.13106,
                    1.65952,
                    2.42261,
                    3.60536,
                    5.34893,
                    8.96871,
                    10.97202,
                    19.32370,
                    29.01935,
                    38.82026,
                    55.50539,
                    78.44395,
                    109.49257,
                    153.72985,
            };
        }};

// #if ENABLE_CUSTOM_OUTPUTS
        void build3DTable(double rl, final AyYmParam par, final AyYmParam par_env, int normalize, double factor, int zero_is_off, int[] tab) {
            int j, j1, j2, j3, e, indx;
            double rt, rw, n;
            double min = 10.0, max = 0.0;
            double[] temp;

            temp = new double[8 * 32 * 32 * 32];

            for (e = 0; e < 8; e++) {
                final AyYmParam parCh1 = (e & 0x01) != 0 ? par_env : par;
                final AyYmParam parCh2 = (e & 0x02) != 0 ? par_env : par;
                final AyYmParam parCh3 = (e & 0x04) != 0 ? par_env : par;

                for (j1 = 0; j1 < parCh1.resCount; j1++)
                    for (j2 = 0; j2 < parCh2.resCount; j2++)
                        for (j3 = 0; j3 < parCh3.resCount; j3++) {
                            if (zero_is_off != 0) {
                                n = (j1 != 0 || (e & 0x01) != 0) ? 1 : 0;
                                n += (j2 != 0 || (e & 0x02) != 0) ? 1 : 0;
                                n += (j3 != 0 || (e & 0x04) != 0) ? 1 : 0;
                            } else
                                n = 3.0;

                            rt = n / par.rUp + 3.0 / par.rDown + 1.0 / rl;
                            rw = n / par.rUp;

                            rw += 1.0 / parCh1.res[j1];
                            rt += 1.0 / parCh1.res[j1];
                            rw += 1.0 / parCh2.res[j2];
                            rt += 1.0 / parCh2.res[j2];
                            rw += 1.0 / parCh3.res[j3];
                            rt += 1.0 / parCh3.res[j3];

                            indx = (e << 15) | (j3 << 10) | (j2 << 5) | j1;
                            temp[indx] = rw / rt;
                            if (temp[indx] < min)
                                min = temp[indx];
                            if (temp[indx] > max)
                                max = temp[indx];
                        }
            }

            if (normalize != 0) {
                for (j = 0; j < 32 * 32 * 32 * 8; j++)
                    tab[j] = (int) (MAX_OUTPUT * (((temp[j] - min) / (max - min))) * factor / NUM_CHANNELS);
            } else {
                for (j = 0; j < 32 * 32 * 32 * 8; j++)
                    tab[j] = (int) (MAX_OUTPUT * temp[j] / NUM_CHANNELS);
            }

            /* for (e=0;e<16;e++) printf("%d %d\n",e<<10, tab[e<<10]); */

        }
// #endif

// #if ENABLE_CUSTOM_OUTPUTS
        void buildMosfetResistorTable(final MosfetParam par, final double rd, int[] tab) {
            int j;

            for (j = 0; j < par.count; j++) {
                final double vd = 5.0;
                final double vg = par.vg - par.vth;
                final double kn = par.kn[j] / 1.0e6;
                final double p2 = 1.0 / (2.0 * kn * rd) + vg;
                final double vs = p2 - Math.sqrt(p2 * p2 - vg * vg);

                final double res = rd * (vd / vs - 1.0);
                /* That's the biggest value we can stream on to netlist. */

                if (res > (1 << 28))
                    tab[j] = (1 << 28);
                else
                    tab[j] = (int) res;
                //printf("%d %f %10d\n", j, rd / (res + rd) * 5.0, tab[j]);
            }
        }
// #endif
    }
}
