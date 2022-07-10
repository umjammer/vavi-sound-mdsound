/*
 * license:BSD-3-Clause
 *
 * copyright-holders:Bryan McPhail
 */

package mdsound.chips;


/**
 * Konami 005289 - SCC Sound as used in Bubblesystem
 * <p>
 * This file is pieced together by Bryan McPhail from a combination of
 * Namco Sound, Amuse by Cab, Nemesis schematics and whoever first
 * figured out SCC!
 * The 005289 is a 2 channel Sound generator. Each channel gets its
 * waveform from a prom (4 bits wide).
 * (From Nemesis schematics)
 * Address lines A0-A4 of the prom run to the 005289, giving 32 bytes
 * per waveform.  Address lines A5-A7 of the prom run to PA5-PA7 of
 * the AY8910 control port A, giving 8 different waveforms. PA0-PA3
 * of the AY8910 control volume.
 * The second channel is the same as above except port B is used.
 * The 005289 has 12 address inputs and 4 control inputs: LD1, LD2, TG1, TG2.
 * It has no data bus, so data values written don't matter.
 * When LD1 or LD2 is asserted, the 12 bit value on the address bus is
 * latched. Each of the two channels has its own latch.
 * When TG1 or TG2 is asserted, the frequency of the respective channel is
 * set to the previously latched value.
 * The 005289 itself is nothing but an address generator. Digital to analog
 * conversion, volume control and mixing of the channels is all done
 * externally via resistor networks and 4066 switches and is only implemented
 * here for convenience.
 */
public class K005289 {
    private byte[] soundPRom = null;
    private int rate;

    /* mixer tables and internal buffers */
    private short[] mixerTable;
    private short mixerLookup;
    private short[] mixerBuffer;

    private int[] counter = new int[2];
    private int[] frequency = new int[2];
    private int[] freqLatch = new int[2];
    private int[] waveForm = new int[2];
    private byte[] volume = new byte[2];

    // is this an actual hardware limit? or just an arbitrary divider
    // to bring the output frequency down to a reasonable value for MAME?
    private static final int CLOCK_DIVIDER = 32;

    /**
     * device-specific startup
     */
    public void start() {
        /* get stream channels */
        rate = clock() / CLOCK_DIVIDER;
        //m_stream = stream_alloc(0, 1, m_rate);

        /* allocate a Pair of buffers to mix into - 1 second's worth should be more than enough */
        mixerBuffer = new short[2 * rate];

        /* build the mixer table */
        makeMixerTable(2);

        /* reset all the voices */
        for (int i = 0; i < 2; i++) {
            counter[i] = 0;
            frequency[i] = 0;
            freqLatch[i] = 0;
            waveForm[i] = i * 0x100;
            volume[i] = 0;
        }
    }

    private int clock() {
        throw new UnsupportedOperationException();
    }

    /**
     * handle a stream update
     */
    public void update(int[][] inputs, int[][] outputs, int samples) {
        int[] buffer = outputs[0];
        short mix;
        int i, v, f;

        /* zap the contents of the mixer buffer */
        for (i = 0; i < samples; i++) mixerBuffer[i] = 0;

        v = volume[0];
        f = frequency[0];
        if (v != 0 && f != 0) {
            int w = waveForm[0];
            int c = counter[0];

            mix = 0;// m_mixer_buffer

            /* add our contribution */
            for (i = 0; i < samples; i++) {
                int offs;

                c += CLOCK_DIVIDER;
                offs = (c / f) & 0x1f;
                //m_mixer_buffer[mix++] += (byte)(((w[offs] & 0x0f) - 8) * v);
                mixerBuffer[mix++] += (byte) (((soundPRom[w + offs] & 0x0f) - 8) * v);
            }

            /* update the counter for this Voice */
            counter[0] = c % (f * 0x20);
        }

        v = volume[1];
        f = frequency[1];
        if (v != 0 && f != 0) {
            int w = waveForm[1];
            int c = counter[1];

            mix = 0;// m_mixer_buffer

            /* add our contribution */
            for (i = 0; i < samples; i++) {
                int offs;

                c += CLOCK_DIVIDER;
                offs = (c / f) & 0x1f;
                mixerBuffer[mix++] += (byte) (((soundPRom[w + offs] & 0x0f) - 8) * v);
            }

            /* update the counter for this Voice */
            counter[1] = c % (f * 0x20);
        }

        /* mix it down */
        mix = 0;
        for (i = 0; i < samples; i++)
            buffer[i] = mixerTable[mixerLookup + mixerBuffer[mix++]];
    }

    /**
     * build a table to divide by the number of voices
     */
    private void makeMixerTable(int voices) {
        int count = voices * 128;
        int i;
        int gain = 16;

        /* allocate memory */
        mixerTable = new short[256 * voices];

        /* find the middle of the table */
        mixerLookup = (short) (128 * voices);

        /* fill in the table - 16 bit case */
        for (i = 0; i < count; i++) {
            int val = i * gain * 16 / voices;
            if (val > 32767) val = 32767;
            mixerTable[128 * voices + i] = (short) val;
            mixerTable[128 * voices - i] = (short) -val;
        }
    }

    public void writeControlA(byte data) {
        volume[0] = (byte) (data & 0xf);
        waveForm[0] = data & 0xe0;
    }

    public void writeControlB(byte data) {
        volume[1] = (byte) (data & 0xf);
        waveForm[1] = (data & 0xe0) + 0x100;
    }

    public void writeLd1(int offset, byte data) {
        freqLatch[0] = 0xfff - offset;
    }

    public void writeLd2(int offset, byte data) {
        freqLatch[1] = 0xfff - offset;
    }

    public void writeTg1(byte data) {
        frequency[0] = freqLatch[0];
    }

    public void writeTg2(byte data) {
        frequency[1] = freqLatch[1];
    }
}
