package MDSound;

	public class K005289 extends Instrument
	{
		@Override public String getName() { return "K005289"; }
		@Override public String getShortName() { return "K005"; }

		@Override public void Reset(byte ChipID)
		{
			throw new UnsupportedOperationException();
		}

		@Override public int Start(byte ChipID, int clock)
		{
			throw new UnsupportedOperationException();
		}

		@Override public int Start(byte ChipID, int clock, int ClockValue, Object... option)
		{
			throw new UnsupportedOperationException();
		}

		@Override public void Stop(byte ChipID)
		{
			throw new UnsupportedOperationException();
		}

		@Override public void Update(byte ChipID, int[][] outputs, int samples)
		{
			throw new UnsupportedOperationException();
		}

		@Override public int Write(byte ChipID, int port, int adr, int data)
		{
			throw new UnsupportedOperationException();
		}




		private byte[] m_sound_prom = null;
		//private sound_stream* m_stream;
		private int m_rate;

		/* mixer tables and internal buffers */
		short[] m_mixer_table;
		short m_mixer_lookup;
		short[] m_mixer_buffer;

		private int[] m_counter = new int[2];
		private int[] m_frequency = new int[2];
		private int[] m_freq_latch = new int[2];
		private int[] m_waveform = new int[2];
		private byte[] m_volume = new byte[2];




		// license:BSD-3-Clause
		// copyright-holders:Bryan McPhail
		/***************************************************************************
			Konami 005289 - SCC sound as used in Bubblesystem
			This file is pieced together by Bryan McPhail from a combination of
			Namco Sound, Amuse by Cab, Nemesis schematics and whoever first
			figured out SCC!
			The 005289 is a 2 channel sound generator. Each channel gets its
			waveform from a prom (4 bits wide).
			(From Nemesis schematics)
			Address lines A0-A4 of the prom run to the 005289, giving 32 bytes
			per waveform.  Address lines A5-A7 of the prom run to PA5-PA7 of
			the AY8910 control port A, giving 8 different waveforms. PA0-PA3
			of the AY8910 control volume.
			The second channel is the same as above except port B is used.
			The 005289 has 12 address inputs and 4 control inputs: LD1, LD2, TG1, TG2.
			It has no data bus, so data values written don't matter.
			When LD1 or LD2 is asserted, the 12 bit value on the address bus is
			latched. Each of the two channels has its own latch.
			When TG1 or TG2 is asserted, the frequency of the respective channel is
			set to the previously latched value.
			The 005289 itself is nothing but an address generator. Digital to analog
			conversion, volume control and mixing of the channels is all done
			externally via resistor networks and 4066 switches and is only implemented
			here for convenience.
		***************************************************************************/

		//# include "emu.h"
		//# include "k005289.h"

		// is this an actual hardware limit? or just an arbitrary divider
		// to bring the output frequency down to a reasonable value for MAME?
		private int CLOCK_DIVIDER = 32;

		// device type definition
		//DEFINE_DEVICE_TYPE(K005289, k005289_device, "k005289", "K005289 SCC")


		//**************************************************************************
		//  LIVE DEVICE
		//**************************************************************************

		//-------------------------------------------------
		//  k005289_device - constructor
		//-------------------------------------------------

		//	private void k005289_device(final machine_config &mconfig, final char* tag, device_t *owner, (int)32_t clock)
		//: device_t(mconfig, K005289, tag, owner, clock)
		//, device_sound_interface(mconfig, *this)
		//, m_sound_prom(*this, DEVICE_SELF)
		//, m_stream(nullptr)
		//, m_rate(0)
		//, m_mixer_table(nullptr)
		//, m_mixer_lookup(nullptr)
		//, m_mixer_buffer(nullptr)
		//	{
		//	}


		//-------------------------------------------------
		//  device_start - device-specific startup
		//-------------------------------------------------

		private void device_start()
		{
			/* get stream channels */
			m_rate = clock() / CLOCK_DIVIDER;
			//m_stream = stream_alloc(0, 1, m_rate);

			/* allocate a pair of buffers to mix into - 1 second's worth should be more than enough */
			m_mixer_buffer = new short[2 * m_rate];

			/* build the mixer table */
			make_mixer_table(2);

			/* reset all the voices */
			for (int i = 0; i < 2; i++)
			{
				m_counter[i] = 0;
				m_frequency[i] = 0;
				m_freq_latch[i] = 0;
				m_waveform[i] = (int)(i * 0x100);
				m_volume[i] = 0;
			}

			//save_item(NAME(m_counter));
			//save_item(NAME(m_frequency));
			//save_item(NAME(m_freq_latch));
			//save_item(NAME(m_waveform));
			//save_item(NAME(m_volume));
		}

		private int clock()
		{
			throw new UnsupportedOperationException();
		}


		//-------------------------------------------------
		//  sound_stream_update - handle a stream update
		//-------------------------------------------------

		private void sound_stream_update(int[][] inputs, int[][] outputs, int samples)
		{
			int[] buffer = outputs[0];
			short mix;
			int i, v, f;

			/* zap the contents of the mixer buffer */
			for (i = 0; i < samples; i++) m_mixer_buffer[i] = 0;

			v = m_volume[0];
			f = m_frequency[0];
			if (v != 0 && f != 0)
			{
				//byte[] w = m_sound_prom[m_waveform[0]];
				int w = m_waveform[0];
				int c = (int)m_counter[0];

				mix = 0;// m_mixer_buffer

				/* add our contribution */
				for (i = 0; i < samples; i++)
				{
					int offs;

					c += CLOCK_DIVIDER;
					offs = (c / f) & 0x1f;
					//m_mixer_buffer[mix++] += (byte)(((w[offs] & 0x0f) - 8) * v);
					m_mixer_buffer[mix++] += (byte)(((m_sound_prom[w + offs] & 0x0f) - 8) * v);
				}

				/* update the counter for this voice */
				m_counter[0] = (int)(c % (f * 0x20));
			}

			v = m_volume[1];
			f = m_frequency[1];
			if (v != 0 && f != 0)
			{
				//byte[] w = m_sound_prom[m_waveform[1]];
				int w = m_waveform[1];
				int c = (int)m_counter[1];

				mix = 0;// m_mixer_buffer

				/* add our contribution */
				for (i = 0; i < samples; i++)
				{
					int offs;

					c += CLOCK_DIVIDER;
					offs = (c / f) & 0x1f;
					//m_mixer_buffer[mix++] += (byte)(((w[offs] & 0x0f) - 8) * v);
					m_mixer_buffer[mix++] += (byte)(((m_sound_prom[w + offs] & 0x0f) - 8) * v);
				}

				/* update the counter for this voice */
				m_counter[1] = (int)(c % (f * 0x20));
			}

			/* mix it down */
			mix = 0;// m_mixer_buffer
			for (i = 0; i < samples; i++)
				buffer[i] = m_mixer_table[m_mixer_lookup + m_mixer_buffer[mix++]];
		}




		/********************************************************************************/

		/* build a table to divide by the number of voices */
		private void make_mixer_table(int voices)
		{
			int count = voices * 128;
			int i;
			int gain = 16;

			/* allocate memory */
			m_mixer_table = new short[256 * voices];

			/* find the middle of the table */
			m_mixer_lookup = (short)(128 * voices);// m_mixer_table.get() + (128 * voices);

			/* fill in the table - 16 bit case */
			for (i = 0; i < count; i++)
			{
				int val = i * gain * 16 / voices;
				if (val > 32767) val = 32767;
				//m_mixer_lookup[i] = val;
				//m_mixer_lookup[-i] = -val;
				m_mixer_table[128 * voices + i] = (short)val;
				m_mixer_table[128 * voices - i] = (short)-val;
			}
		}


		private void control_A_w(byte data)
		{
			//m_stream.update();

			m_volume[0] = (byte)(data & 0xf);
			m_waveform[0] = (int)(data & 0xe0);
		}


		private void control_B_w(byte data)
		{
			//m_stream.update();

			m_volume[1] = (byte)(data & 0xf);
			m_waveform[1] = (int)((data & 0xe0) + 0x100);
		}


		private void ld1_w(int offset, byte data)
		{
			m_freq_latch[0] = (int)(0xfff - offset);
		}


		private void ld2_w(int offset, byte data)
		{
			m_freq_latch[1] = (int)(0xfff - offset);
		}


		private void tg1_w(byte data)
		{
			//m_stream.update();

			m_frequency[0] = m_freq_latch[0];
		}


		private void tg2_w(byte data)
		{
			//m_stream.update();

			m_frequency[1] = m_freq_latch[1];
		}
	}
