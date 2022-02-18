package MDSound;

import java.util.Arrays;

public class Qsound_ctr extends Instrument
	{
		@Override public String getName() {return "QSound_ctr"; }
		@Override public String getShortName() { return "QSNDc"; }

		@Override public void Reset(byte ChipID)
		{
			device_reset_qsound(ChipID);

			visVolume = new int[][][] {
				new int[][] { new int[] { 0, 0 } }
				, new int[][] { new int[] { 0, 0 } }
			};
		}

		@Override public int Start(byte ChipID, int clock)
		{
			return (int)device_start_qsound(ChipID, (int)4000000);
		}

		@Override public int Start(byte ChipID, int clock, int ClockValue, Object... option)
		{
			return (int)device_start_qsound(ChipID, (int)ClockValue);
		}

		@Override public void Stop(byte ChipID)
		{
			device_stop_qsound(ChipID);
		}

		@Override public void Update(byte ChipID, int[][] outputs, int samples)
		{
			qsound_update(ChipID, outputs, samples);

			visVolume[ChipID][0][0] = outputs[0][0];
			visVolume[ChipID][0][1] = outputs[1][0];
		}

		@Override public int Write(byte ChipID, int port, int adr, int data)
		{
			qsound_w(ChipID, adr, (byte)data);
			return 0;
		}




		//byte EMU_CORE = 0x00;
		// fix broken optimization of old VGMs causing problems with the new core
		byte key_on_hack = 0x00;
		int[][] start_addr_cache = new int[][] { new int[16], new int[16] };
		int[][] pitch_cache = new int[][] { new int[16], new int[16] };
		int[] data_latch = new int[2];


		private int device_start_qsound(byte ChipID, int clock)
		{
			start_addr_cache = new int[][] { new int[16], new int[16] };
			pitch_cache = new int[][] { new int[16], new int[16] };

			if (clock < 10000000)
			{
				clock *= 15;
				key_on_hack = 1;
			}
			return device_start_qsound_ctr(ChipID, clock);
		}


		private void device_stop_qsound(byte ChipID)
		{
			device_stop_qsound_ctr(ChipID);
			return;
		}

		private void device_reset_qsound(byte ChipID)
		{
			device_reset_qsound_ctr(ChipID);
			// need to wait until the chip is ready before we start writing to it ...
			// we do this by time travel.
			qsoundc_wait_busy(ChipID);
		}

		public void qsound_w(byte ChipID, int offset, byte data)
		{
			if (key_on_hack != 0)
			{
				int ch;
				switch (offset)
				{
					// need to handle three cases, as vgm_cmp can remove writes to both phase and bank
					// registers, depending on version.
					// - start address was written before end/loop, but phase register is written
					// - as above, but phase is not written (we use bank as a backup then)
					// - voice parameters are written during a note (we can't rewrite the address then)
					case 0:
						data_latch[ChipID] = (int)((data_latch[ChipID] & 0x00ff) | (data << 8));
						break;
					case 1:
						data_latch[ChipID] = (int)((data_latch[ChipID] & 0xff00) | data);
						break;
					case 2:
						if (data > 0x7f)
							break;
						ch = data >> 3;

						switch (data & 7)
						{
							case 1: // Start addr. write
								start_addr_cache[ChipID][ch] = data_latch[ChipID];
								break;
							case 2: // Pitch write
									// (old HLE assumed writing a non-zero value after a zero value was Key On)
								if (pitch_cache[ChipID][ch] == 0 && data_latch[ChipID] != 0)
									qsoundc_write_data(ChipID, (byte)((ch << 3) + 1), start_addr_cache[ChipID][ch]);
								pitch_cache[ChipID][ch] = data_latch[ChipID];
								break;
							case 3: // Phase (old HLE also assumed this was Key On)
								qsoundc_write_data(ChipID, (byte)((ch << 3) + 1), start_addr_cache[ChipID][ch]);
								break;
							default:
								break;
						}
						break;
				}
			}
			qsoundc_w(ChipID, offset, data);

			// need to wait until the chip is ready before we start writing to it ...
			// we do this by time travel.
			if (offset == 2 && data == 0xe3)
				qsoundc_wait_busy(ChipID);

		}

		private void qsound_update(byte ChipID, int[][] outputs, int samples)
		{
			qsoundc_update(ChipID, outputs, samples);
			return;
		}



		public void qsound_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData)
		{
			qsoundc_write_rom(ChipID, ROMSize, DataStart, DataLength, ROMData, 0); return;
		}

		public void qsound_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int srcStartAddress)
		{
			qsoundc_write_rom(ChipID, ROMSize, DataStart, DataLength, ROMData, srcStartAddress); return;
		}

		public void qsound_set_mute_mask(byte ChipID, int MuteMask)
		{
			qsoundc_set_mute_mask(ChipID, MuteMask); return;
		}



		/*
		Capcom DL-1425 QSound emulator
		==============================

		by superctr (Ian Karlsson)
		with thanks to Valley Bell
		2018-05-12 - 2018-05-15
		*/

		////#include "emu.h"
		//# include "mamedef.h"
		//# ifdef _DEBUG
		//# include <stdio.h>
		//#endif
		//# include <stdlib.h>
		//# include <string.h>	// for memset
		//# include <stddef.h>	// for NULL
		//# include <math.h>
		//# include "qsound_ctr.h"


		private int CLAMP(int x, int low, int high)
		{
			//return (((x) > (high)) ? (high) : (((x) < (low)) ? (low) : (x)));
			if (x > high) 
				return high;
			if (x < low) 
				return low;
			return x;
		}

		//class qsound_voice
		//{
			//public int bank = 0;
			//public int addr = 0; // top word is the sample address
			//public int phase = 0;
			//public int rate = 0;
			//public short loop_len = 0;
			//public short end_addr = 0;
			//public short volume = 0;
			//public short echo = 0;
		//};

		class qsound_adpcm
		{
			//public int start_addr = 0;
			//public int end_addr = 0;
			//public int bank = 0;
			//public short volume = 0;
			//public int flag = 0;
			public short cur_vol = 0;
			public short step_size = 0;
			public int cur_addr = 0;
		};

		// Q1 Filter
		class qsound_fir
		{
			public int tap_count = 0;  // usually 95
			public int delay_pos = 0;
			//public short table_pos = 0;
			public short[] taps = new short[95];
			public short[] delay_line = new short[95];
		};

		// Delay line
		class qsound_delay
		{
			//public short delay;
			//public short volume;
			public short write_pos;
			public short read_pos;
			public short[] delay_line = new short[51];
		};

		class qsound_echo
		{
			//public int end_pos;

			//public short feedback;
			public short length;
			public short last_sample;
			public short[] delay_line = new short[1024];
			public short delay_pos;
		};

		class qsound_chip
		{

			public byte[] romData;
			public int romSize;
			public int romMask;
			public int muteMask;

			// ==================================================== //

			public int data_latch;
			public short[] _out = new short[2];

			public short[][][] pan_tables = new short[][][]{
				new short[][]{ new short[98], new short[98] },
				new short[][]{ new short[98], new short[98] }
			};

			//public qsound_voice[] voice = new qsound_voice[16];
			public qsound_adpcm[] adpcm = new qsound_adpcm[3];

			//public int[] voice_pan = new int[16 + 3];
			public short[] voice_output = new short[16 + 3];

			public qsound_echo echo=new qsound_echo();

			public qsound_fir[] filter = new qsound_fir[2];
			public qsound_fir[] alt_filter = new qsound_fir[2];

			public qsound_delay[] wet = new qsound_delay[2];
			public qsound_delay[] dry = new qsound_delay[2];

			public int state;
			//public int next_state;

			//public int delay_update;

			public int state_counter;
			public byte ready_flag;

			public int[] register_map = new int[256];

			public qsound_chip()
            {
				//for (int i = 0; i < voice.length; i++) voice[i] = new qsound_voice();
				for (int i = 0; i < adpcm.length; i++) adpcm[i] = new qsound_adpcm();
				for (int i = 0; i < filter.length; i++) filter[i] = new qsound_fir();
				for (int i = 0; i < alt_filter.length; i++) alt_filter[i] = new qsound_fir();
				for (int i = 0; i < wet.length; i++) wet[i] = new qsound_delay();
				for (int i = 0; i < dry.length; i++) dry[i] = new qsound_delay();
			}
		};

		//static void init_pan_tables(struct qsound_chip *chip);
		//static void init_register_map(struct qsound_chip *chip);
		//static void update_sample(struct qsound_chip *chip);

		//static void state_init(struct qsound_chip *chip);
		//static void state_refresh_filter_1(struct qsound_chip *chip);
		//static void state_refresh_filter_2(struct qsound_chip *chip);
		//static void state_normal_update(struct qsound_chip *chip);

		//INLINE INT16 get_sample(struct qsound_chip *chip, int bank, int address);
		//INLINE final INT16* get_filter_table(struct qsound_chip *chip, int offset);
		//INLINE INT16 pcm_update(struct qsound_chip *chip, int voice_no, int *echo_out);
		//INLINE void adpcm_update(struct qsound_chip *chip, int voice_no, int nibble);
		//INLINE INT16 echo(struct qsound_echo *r,int input);
		//INLINE int fir(struct qsound_fir *f, INT16 input);
		//INLINE int delay(struct qsound_delay *d, int input);
		//INLINE void delay_update(struct qsound_delay *d);

		// ****************************************************************************

		private final int MAX_CHIPS = 0x02;
		qsound_chip[] QSoundData = new Qsound_ctr.qsound_chip[MAX_CHIPS];

		private int device_start_qsound_ctr(byte ChipID, int clock)
		{
			QSoundData[ChipID] = new qsound_chip();
			qsound_chip chip = QSoundData[ChipID];
			//memset(chip,0,sizeof(* chip));

			chip.romData = null;
			chip.romSize = 0x00;
			chip.romMask = 0x00;

			qsoundc_set_mute_mask(ChipID, 0x00000);

			init_pan_tables(chip);
			init_register_map(chip);

			return clock / 2 / 1248;
		}

		private void device_stop_qsound_ctr(byte ChipID)
		{
			qsound_chip chip = QSoundData[ChipID];
			//free(chip.romData);
			return;
		}

		private void device_reset_qsound_ctr(byte ChipID)
		{
			qsound_chip chip = QSoundData[ChipID];

			chip.ready_flag = 0;
			chip._out[0] = chip._out[1] = 0;
			chip.state = 0;
			chip.state_counter = 0;

			return;
		}

		private byte qsoundc_r(byte ChipID, int offset)
		{
			qsound_chip chip = QSoundData[ChipID];

			return chip.ready_flag;
		}

		private void qsoundc_w(byte ChipID, int offset, byte data)
		{
			qsound_chip chip = QSoundData[ChipID];

			switch (offset)
			{
				case 0:
					chip.data_latch = (int)((chip.data_latch & 0x00ff) | (data << 8));
					break;
				case 1:
					chip.data_latch = (int)((chip.data_latch & 0xff00) | data);
					break;
				case 2:
					qsoundc_write_data(ChipID, data, chip.data_latch);
					break;
				default:
					break;
			}

			return;
		}

		private void qsoundc_write_data(byte ChipID, byte address, int data)
		{
			qsound_chip chip = QSoundData[ChipID];

			int[] destination = chip.register_map;
			if (destination != null) destination[address] = data;
			chip.ready_flag = 0;

			return;
		}

		private void qsoundc_update(byte ChipID, int[][] outputs, int samples)
		{
			qsound_chip chip = QSoundData[ChipID];
			int curSmpl;

			//memset(outputs[0], 0, samples* sizeof(* outputs[0]));
			//memset(outputs[1], 0, samples* sizeof(* outputs[1]));

			for (curSmpl = 0; curSmpl < samples; curSmpl++)
			{
				outputs[0][curSmpl] = 0;
				outputs[1][curSmpl] = 0;

				update_sample(chip);

				outputs[0][curSmpl] = chip._out[0];
				outputs[1][curSmpl] = chip._out[1];
			}

			return;
		}

		private void qsoundc_write_rom(byte ChipID, int ROMSize, int DataStart, int DataLength, byte[] ROMData, int srcStartAddress)
		{
			qsound_chip chip = QSoundData[ChipID];

			if (chip.romSize != ROMSize)
			{
				chip.romData = new byte[ROMSize];// ((int)8*) realloc(chip.romData, ROMSize);
				chip.romSize = (int)ROMSize;
				for (int i = 0; i < chip.romData.length; i++) chip.romData[i] = (byte) 0xff;
				chip.romMask = (int)0xffff_ffff;// -1;
			}
			if (DataStart > ROMSize)
				return;
			if (DataStart + DataLength > ROMSize)
				DataLength = ROMSize - DataStart;

			for (int i = 0; i < DataLength; i++) chip.romData[i + DataStart] = ROMData[i + srcStartAddress];

			return;
		}

		private void qsoundc_set_mute_mask(byte ChipID, int MuteMask)
		{
			qsound_chip chip = QSoundData[ChipID];
			if (chip == null) return;
			chip.muteMask = MuteMask;

			return;
		}

		private void qsoundc_wait_busy(byte ChipID)
		{
			qsound_chip chip = QSoundData[ChipID];

			while (chip.ready_flag == 0)
			{
				update_sample(chip);
			}
		}

		// ============================================================================

		private final static short[] qsound_dry_mix_table = new short[] {
			-16384,-16384,-16384,-16384,-16384,-16384,-16384,-16384,
			-16384,-16384,-16384,-16384,-16384,-16384,-16384,-16384,
			-16384,-14746,-13107,-11633,-10486,-9175,-8520,-7209,
			-6226,-5226,-4588,-3768,-3277,-2703,-2130,-1802,
			0
		};

		private final static short[] qsound_wet_mix_table = new short[] {
			0,-1638,-1966,-2458,-2949,-3441,-4096,-4669,
			-4915,-5120,-5489,-6144,-7537,-8831,-9339,-9830,
			-10240,-10322,-10486,-10568,-10650,-11796,-12288,-12288,
			-12534,-12648,-12780,-12829,-12943,-13107,-13418,-14090,
			-16384
		};

		private final static short[] qsound_linear_mix_table = new short[] {
			-16379,-16338,-16257,-16135,-15973,-15772,-15531,-15251,
			-14934,-14580,-14189,-13763,-13303,-12810,-12284,-11729,
			-11729,-11144,-10531,-9893,-9229,-8543,-7836,-7109,
			-6364,-5604,-4829,-4043,-3246,-2442,-1631,-817,
			0
		};

		private final static short[][] qsound_filter_data = new short[][]{
			new short[]{	// d53 - 0
				0,0,0,6,44,-24,-53,-10,59,-40,-27,1,39,-27,56,127,174,36,-13,49,
				212,142,143,-73,-20,66,-108,-117,-399,-265,-392,-569,-473,-71,95,-319,-218,-230,331,638,
				449,477,-180,532,1107,750,9899,3828,-2418,1071,-176,191,-431,64,117,-150,-274,-97,-238,165,
				166,250,-19,4,37,204,186,-6,140,-77,-1,1,18,-10,-151,-149,-103,-9,55,23,
				-102,-97,-11,13,-48,-27,5,18,-61,-30,64,72,0,0,0,
			},
			new short[]{	// db2 - 1 - default left filter
				0,0,0,85,24,-76,-123,-86,-29,-14,-20,-7,6,-28,-87,-89,-5,100,154,160,
				150,118,41,-48,-78,-23,59,83,-2,-176,-333,-344,-203,-66,-39,2,224,495,495,280,
				432,1340,2483,5377,1905,658,0,97,347,285,35,-95,-78,-82,-151,-192,-171,-149,-147,-113,
				-22,71,118,129,127,110,71,31,20,36,46,23,-27,-63,-53,-21,-19,-60,-92,-69,
				-12,25,29,30,40,41,29,30,46,39,-15,-74,0,0,0,
			},
			new short[]{	// e11 - 2 - default right filter
				0,0,0,23,42,47,29,10,2,-14,-54,-92,-93,-70,-64,-77,-57,18,94,113,
				87,69,67,50,25,29,58,62,24,-39,-131,-256,-325,-234,-45,58,78,223,485,496,
				127,6,857,2283,2683,4928,1328,132,79,314,189,-80,-90,35,-21,-186,-195,-99,-136,-258,
				-189,82,257,185,53,41,84,68,38,63,77,14,-60,-71,-71,-120,-151,-84,14,29,
				-8,7,66,69,12,-3,54,92,52,-6,-15,-2,0,0,0,
			},
			new short[]{	// e70 - 3
				0,0,0,2,-28,-37,-17,0,-9,-22,-3,35,52,39,20,7,-6,2,55,121,
				129,67,8,1,9,-6,-16,16,66,96,118,130,75,-47,-92,43,223,239,151,219,
				440,475,226,206,940,2100,2663,4980,865,49,-33,186,231,103,42,114,191,184,116,29,
				-47,-72,-21,60,96,68,31,32,63,87,76,39,7,14,55,85,67,18,-12,-3,
				21,34,29,6,-27,-49,-37,-2,16,0,-21,-16,0,0,0,
			},
			new short[]{	// ecf - 4
				0,0,0,48,7,-22,-29,-10,24,54,59,29,-36,-117,-185,-213,-185,-99,13,90,
				83,24,-5,23,53,47,38,56,67,57,75,107,16,-242,-440,-355,-120,-33,-47,152,
				501,472,-57,-292,544,1937,2277,6145,1240,153,47,200,152,36,64,134,74,-82,-208,-266,
				-268,-188,-42,65,74,56,89,133,114,44,-3,-1,17,29,29,-2,-76,-156,-187,-151,
				-85,-31,-5,7,20,32,24,-5,-20,6,48,62,0,0,0,
			}
		};

		private final static short[] qsound_filter_data2 = new short[] {
			// f2e - following 95 values used for "disable output" filter
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,
	
			// f73 - following 45 values used for "mode 2" filter (overlaps with f2e)
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,
			-371,-196,-268,-512,-303,-315,-184,-76,276,-256,298,196,990,236,1114,-126,4377,6549,791,
	
			// fa0 - filtering disabled (for 95-taps) (use fa3 or fa4 for mode2 filters)
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,-16384,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
		};

		private final static short[] adpcm_step_table = new short[] {
			154, 154, 128, 102, 77, 58, 58, 58,
			58, 58, 58, 58, 77, 102, 128, 154
		};

		// DSP states
		enum STATE
		{
			INIT1( 0x288),
			INIT2 (0x61a),
			REFRESH1 ( 0x039),
			REFRESH2 ( 0x04f),
			NORMAL1 ( 0x314),
			NORMAL2 ( 0x6b2);
            int v;
            STATE(int v) { this.v = v; }
            static STATE valueOf(int v) { return Arrays.stream(values()).filter(e -> e.v == v).findFirst().get(); }
		}

		enum PANTBL
		{
			LEFT(0),
			RIGHT ( 1),
			DRY(0),
			WET (1);
            int v;
            PANTBL(int v) { this.v = v; }
		}

		private void init_pan_tables(qsound_chip chip)
		{
			int i;
			for (i = 0; i < 33; i++)
			{
				// dry mixing levels
				chip.pan_tables[(int)PANTBL.LEFT.v][(int)PANTBL.DRY.v][i] = qsound_dry_mix_table[i];
				chip.pan_tables[(int)PANTBL.RIGHT.v][(int)PANTBL.DRY.v][i] = qsound_dry_mix_table[32 - i];
				// wet mixing levels
				chip.pan_tables[(int)PANTBL.LEFT.v][(int)PANTBL.WET.v][i] = qsound_wet_mix_table[i];
				chip.pan_tables[(int)PANTBL.RIGHT.v][(int)PANTBL.WET.v][i] = qsound_wet_mix_table[32 - i];
				// linear panning, only for dry component. wet component is muted.
				chip.pan_tables[(int)PANTBL.LEFT.v][(int)PANTBL.DRY.v][i + 0x30] = qsound_linear_mix_table[i];
				chip.pan_tables[(int)PANTBL.RIGHT.v][(int)PANTBL.DRY.v][i + 0x30] = qsound_linear_mix_table[32 - i];
			}
		}

		private void init_register_map(qsound_chip chip)
		{
			int i;

			// unused registers
			for (i = 0; i < 256; i++) chip.register_map[i] = 0;// null;

			// PCM registers
			for (i = 0; i < 16; i++) // PCM voices
			{
				//chip.register_map[(i << 3) + 0] = (int)chip.voice[(i + 1) % 16].bank; // Bank applies to the next channel
				//chip.register_map[(i << 3) + 1] = (int)chip.voice[i].addr; // Current sample position and start position.
				//chip.register_map[(i << 3) + 2] = (int)chip.voice[i].rate; // 4.12 fixed point decimal.
				//chip.register_map[(i << 3) + 3] = (int)chip.voice[i].phase;
				//chip.register_map[(i << 3) + 4] = (int)chip.voice[i].loop_len;
				//chip.register_map[(i << 3) + 5] = (int)chip.voice[i].end_addr;
				//chip.register_map[(i << 3) + 6] = (int)chip.voice[i].volume;
				chip.register_map[(i << 3) + 7] = 0;// null; // unused
				//chip.register_map[i + 0x80] = (int)chip.voice_pan[i];
				//chip.register_map[i + 0xba] = (int)chip.voice[i].echo;
			}

			// ADPCM registers
			//for (i = 0; i < 3; i++) // ADPCM voices
			//{
				// ADPCM sample rate is fixed to 8khz. (one channel is updated every third sample)
				//chip.register_map[(i << 2) + 0xca] = (int)chip.adpcm[i].start_addr;
				//chip.register_map[(i << 2) + 0xcb] = (int)chip.adpcm[i].end_addr;
				//chip.register_map[(i << 2) + 0xcc] = (int)chip.adpcm[i].bank;
				//chip.register_map[(i << 2) + 0xcd] = (int)chip.adpcm[i].volume;
				//chip.register_map[i + 0xd6] = (int)chip.adpcm[i].flag; // non-zero to start ADPCM playback
				//chip.register_map[i + 0x90] = (int)chip.voice_pan[16 + i];
			//}

			// QSound registers
			//chip.register_map[0x93] = (int)chip.echo.feedback;
			//chip.register_map[0xd9] = (int)chip.echo.end_pos;
			//chip.register_map[0xe2] = (int)chip.delay_update; // non-zero to update delays
			//chip.register_map[0xe3] = (int)chip.next_state;
			//for (i = 0; i < 2; i++)  // left, right
			//{
				// Wet
				//chip.register_map[(i << 1) + 0xda] = (int)chip.filter[i].table_pos;
				//chip.register_map[(i << 1) + 0xde] = (int)chip.wet[i].delay;
				//chip.register_map[(i << 1) + 0xe4] = (int)chip.wet[i].volume;
				// Dry
				//chip.register_map[(i << 1) + 0xdb] = (int)chip.alt_filter[i].table_pos;
				//chip.register_map[(i << 1) + 0xdf] = (int)chip.dry[i].delay;
				//chip.register_map[(i << 1) + 0xe5] = (int)chip.dry[i].volume;
			//}
		}

		private short get_sample(qsound_chip chip, int bank, int address)
		{
			int rom_addr;
			byte sample_data;

			if (chip.romMask == 0) return 0;    // no ROM loaded
			if ((bank & 0x8000) == 0) return 0; // ignore attempts to read from DSP program ROM

			bank &= 0x7FFF;
			rom_addr = (int)((bank << 16) | (address << 0));

			sample_data = rom_addr < chip.romData.length ? chip.romData[rom_addr] : (byte)0;
			//common.write("adr:{0:x10} dat:{1:x02}", rom_addr, sample_data);

			return (short)((sample_data << 8) | (sample_data << 0));    // MAME currently expands the 8 bit ROM data to 16 bits this way.
		}

		//private short[] get_filter_table(qsound_chip chip, int offset)
		//{
		//	int index;

		//	if (offset >= 0xf2e && offset < 0xfff)
		//		return qsound_filter_data2[offset - 0xf2e]; // overlapping filter data

		//	index = (offset - 0xd53) / 95;
		//	if (index >= 0 && index < 5)
		//		return qsound_filter_data[index];   // normal tables

		//	return null;    // no filter found.
		//}

		private Short get_filter_table(qsound_chip chip, int offset)
		{
			int index;

			if (offset >= 0xf2e && offset < 0xfff)
				return qsound_filter_data2[offset - 0xf2e]; // overlapping filter data

			index = (offset - 0xd53) / 95;
			if (index >= 0 && index < 5)
				return qsound_filter_data[index][(offset - 0xd53) % 95];   // normal tables

			return null;    // no filter found.
		}

		/********************************************************************/

		// updates one DSP sample
		private void update_sample(qsound_chip chip)
		{
			switch (STATE.valueOf(chip.state))
			{
				default:
				case INIT1:
				case INIT2:
					state_init(chip);
					return;
				case REFRESH1:
					state_refresh_filter_1(chip);
					return;
				case REFRESH2:
					state_refresh_filter_2(chip);
					return;
				case NORMAL1:
				case NORMAL2:
					state_normal_update(chip);
					return;
			}
		}

		// Initialization routine
		private void state_init(qsound_chip chip)
		{
			int mode = (chip.state == (int)STATE.INIT2.v) ? 1 : 0;
			int i;

			// we're busy for 4 samples, including the filter refresh.
			if (chip.state_counter >= 2)
			{
				chip.state_counter = 0;
				//chip.state = chip.next_state;
				chip.state= chip.register_map[0xe3];
				return;
			}
			else if (chip.state_counter == 1)
			{
				chip.state_counter++;
				return;
			}

			//for (i = 0; i < chip.voice.length; i++) chip.voice[i] = new qsound_voice();
			for (i = 0; i < chip.adpcm.length; i++) chip.adpcm[i] = new qsound_adpcm();
			for (i = 0; i < chip.filter.length; i++) chip.filter[i] = new qsound_fir();
			for (i = 0; i < chip.alt_filter.length; i++) chip.alt_filter[i] = new qsound_fir();
			for (i = 0; i < chip.wet.length; i++) chip.wet[i] = new qsound_delay();
			for (i = 0; i < chip.dry.length; i++) chip.dry[i] = new qsound_delay();
			chip.echo = new qsound_echo();

			for (i = 0; i < 19; i++)
			{
				//chip.voice_pan[i] = 0x120;
				chip.register_map[i + 0x80] = 0x120;
				chip.voice_output[i] = 0;
			}

			for (i = 0; i < 16; i++)
			{
				//chip.voice[i].bank = 0x8000;
				chip.register_map[(i << 3) + 0] = 0x8000;
			}
			for (i = 0; i < 3; i++)
			{
				//chip.adpcm[i].bank = 0x8000;
				chip.register_map[(i << 2) + 0xcc] = 0x8000;
			}
			if (mode == 0)
			{
				// mode 1
				//chip.wet[0].delay = 0;
				//chip.dry[0].delay = 46;
				//chip.wet[1].delay = 0;
				//chip.dry[1].delay = 48;
				chip.register_map[(0 << 1) + 0xde] = 0;
				chip.register_map[(0 << 1) + 0xdf] = 46;
				chip.register_map[(1 << 1) + 0xde] = 0;
				chip.register_map[(1 << 1) + 0xdf] = 48;
				//chip.filter[0].table_pos = 0xdb2;
				//chip.filter[1].table_pos = 0xe11;
				chip.register_map[(0 << 1) + 0xda] = 0xdb2;
				chip.register_map[(1 << 1) + 0xda] = 0xe11;
				//chip.echo.end_pos = 0x554 + 6;
				chip.register_map[0xd9]= 0x554 + 6;
				//chip.next_state = (int)STATE.REFRESH1;
				chip.register_map[0xe3] = (int)STATE.REFRESH1.v;
			}
			else
			{
				// mode 2
				//chip.wet[0].delay = 1;
				//chip.dry[0].delay = 0;
				//chip.wet[1].delay = 0;
				//chip.dry[1].delay = 0;
				chip.register_map[(0 << 1) + 0xde] = 1;
				chip.register_map[(0 << 1) + 0xdf] = 0;
				chip.register_map[(1 << 1) + 0xde] = 0;
				chip.register_map[(1 << 1) + 0xdf] = 0;
				//chip.filter[0].table_pos = 0xf73;
				//chip.filter[1].table_pos = 0xfa4;
				chip.register_map[(0 << 1) + 0xda] = 0xf73;
				chip.register_map[(1 << 1) + 0xda] = 0xfa4;
				//chip.alt_filter[0].table_pos = 0xf73;
				//chip.alt_filter[1].table_pos = 0xfa4;
				chip.register_map[(0 << 1) + 0xdb] = 0xf73;
				chip.register_map[(1 << 1) + 0xdb] = 0xfa4;
				//chip.echo.end_pos = 0x53c + 6;
				chip.register_map[0xd9] = 0x53c + 6;
				//chip.next_state = (int)STATE.REFRESH2;
				chip.register_map[0xe3] = (int)STATE.REFRESH2.v;
			}

			//chip.wet[0].volume = 0x3fff;
			//chip.dry[0].volume = 0x3fff;
			//chip.wet[1].volume = 0x3fff;
			//chip.dry[1].volume = 0x3fff;
			chip.register_map[(0 << 1) + 0xe4] = 0x3fff;
			chip.register_map[(0 << 1) + 0xe5] = 0x3fff;
			chip.register_map[(1 << 1) + 0xe4] = 0x3fff;
			chip.register_map[(1 << 1) + 0xe5] = 0x3fff;

			//chip.delay_update = 1;
			chip.register_map[0xe2] = 1;
			chip.ready_flag = 0;
			chip.state_counter = 1;
		}

		// Updates filter parameters for mode 1
		private void state_refresh_filter_1(qsound_chip chip)
		{
			//short[] table;
			int ch;

			for (ch = 0; ch < 2; ch++)
			{
				chip.filter[ch].delay_pos = 0;
				chip.filter[ch].tap_count = 95;

				//table = get_filter_table(chip, (int)chip.filter[ch].table_pos);
				//if (table != null)
				//{
				//	for (int i = 0; i < 95; i++) chip.filter[ch].taps[i] = table[i];
				//}

				//short? dat = get_filter_table(chip, (int)chip.filter[ch].table_pos);
				Short dat = get_filter_table(chip, (int)chip.register_map[(ch << 1) + 0xda]);
				if (dat != null)
				{
					for (int i = 0; i < 95; i++)
					{
						//dat = get_filter_table(chip, (int)(chip.filter[ch].table_pos + i));
						dat = get_filter_table(chip, (int)(chip.register_map[(ch << 1) + 0xda] + i));
						if (dat == null) break;
						chip.filter[ch].taps[i] = (short)dat;
					}
				}
			}

			//chip.state = chip.next_state = (int)STATE.NORMAL1;
			chip.state = chip.register_map[0xe3] = (int)STATE.NORMAL1.v;
		}

		// Updates filter parameters for mode 2
		private void state_refresh_filter_2(qsound_chip chip)
		{
			//short[] table;
			int ch;

			for (ch = 0; ch < 2; ch++)
			{
				chip.filter[ch].delay_pos = 0;
				chip.filter[ch].tap_count = 45;

				//table = get_filter_table(chip, (int)chip.filter[ch].table_pos);
				//if (table != null)
				//	for (int i = 0; i < 45; i++) chip.filter[ch].taps[i] = table[i];

				//short? dat = get_filter_table(chip, (int)chip.filter[ch].table_pos);
				Short dat = get_filter_table(chip, (int)chip.register_map[(ch << 1) + 0xda]);
				if (dat != null)
				{
					for (int i = 0; i < 45; i++)
					{
						//dat = get_filter_table(chip, (int)(chip.filter[ch].table_pos + i));
						dat = get_filter_table(chip, (int)(chip.register_map[(ch << 1) + 0xda] + i));
						if (dat == null) break;
						chip.filter[ch].taps[i] = (short)dat;
					}
				}

				chip.alt_filter[ch].delay_pos = 0;
				chip.alt_filter[ch].tap_count = 44;

				//table = get_filter_table(chip, (int)chip.alt_filter[ch].table_pos);
				//if (table != null)
				//	for (int i = 0; i < 44; i++) chip.alt_filter[ch].taps[i] = table[i];

				//dat = get_filter_table(chip, (int)chip.alt_filter[ch].table_pos);
				dat = get_filter_table(chip, (int)chip.register_map[(ch << 1) + 0xdb]);
				if (dat != null)
				{
					for (int i = 0; i < 44; i++)
					{
						//dat = get_filter_table(chip, (int)(chip.alt_filter[ch].table_pos + i));
						dat = get_filter_table(chip, (int)(chip.register_map[(ch << 1) + 0xdb] + i));
						if (dat == null) break;
						chip.alt_filter[ch].taps[i] = (short)dat;
					}
				}
			}

			//chip.state = chip.next_state = (int)STATE.NORMAL2;
			chip.state = chip.register_map[0xe3] = (int)STATE.NORMAL2.v;
		}

		// Updates a PCM voice. There are 16 voices, each are updated every sample
		// with full rate and volume control.
		private short pcm_update(qsound_chip chip, int voice_no, int echo_out)
		{
			//if (voice_no != 2) return 0;

			//qsound_voice v = chip.voice[voice_no];
			int new_phase;
			short output;

			if ((chip.muteMask & (1 << voice_no)) != 0)
				return 0;

			// Read sample from rom and apply volume
			//output = (short)((v.volume * get_sample(chip, v.bank, v.addr)) >> 14);
			output = (short)((chip.register_map[(voice_no << 3) + 6] 
				* get_sample(chip, chip.register_map[(((voice_no-1+16)%16) << 3) + 0], chip.register_map[(voice_no << 3) + 1])) >> 14);
			//common.write("output:{0} vadr:{1}", output, chip.register_map[(voice_no << 3) + 1]);

			//if (voice_no == 2)
			//{
				//MDSound.debugMsg = String.format("{0}:{1}:{2}:{3}",
					//chip.register_map[(voice_no << 3) + 6], chip.register_map[(voice_no << 3) + 0], chip.register_map[(voice_no << 3) + 1], chip.register_map[(voice_no << 3) + 5]);
			//}

			//echo_out += (output * v.echo) << 2;
			echo_out += (output * chip.register_map[voice_no + 0xba] ) << 2;

			// Add delta to the phase and loop back if required
			//new_phase = v.rate + ((v.addr << 12) | (v.phase >> 4));
			int a = (int)((chip.register_map[(voice_no << 3) + 1] << 12) | (chip.register_map[(voice_no << 3) + 3] >> 4));
			a = (a & 0x0800_0000) != 0 ? (a | 0xf000_0000) : a;
			new_phase = chip.register_map[(voice_no << 3) + 2] + (int)a;

			//if ((new_phase >> 12) >= v.end_addr)
			if ((new_phase >> 12) >= (short)chip.register_map[(voice_no << 3) + 5])
			{
				//new_phase -= (v.loop_len << 12);
				a = (int)((chip.register_map[(voice_no << 3) + 4] << 12));
				a = (a & 0x0800_0000) != 0 ? (a | 0xf000_0000) : a;
				new_phase -= (int)a;// (chip.register_map[(voice_no << 3) + 4] << 12);
			}

            //if (voice_no == 0)
            //{
				//common.write("Bf:{0}", new_phase);
            //}

			new_phase = CLAMP(new_phase, -0x800_0000, 0x7FF_FFFF);

			//if (voice_no == 0)
			//{
				//common.write("Af:{0}", new_phase);
			//}
			//v.addr = (int)(new_phase >> 12);
			chip.register_map[(voice_no << 3) + 1] = (int)(new_phase >> 12);
			//v.phase = (int)((new_phase << 4) & 0xffff);
			chip.register_map[(voice_no << 3) + 3] = (int)((new_phase << 4) & 0xffff);

			return output;
		}

		// Updates an ADPCM voice. There are 3 voices, one is updated every sample
		// (effectively making the ADPCM rate 1/3 of the master sample rate), and
		// volume is set when starting samples only.
		// The ADPCM algorithm is supposedly similar to Yamaha ADPCM. It also seems
		// like Capcom never used it, so this was not emulated in the earlier QSound
		// emulators.
		private void adpcm_update(qsound_chip chip, int voice_no, int nibble)
		{
			//return;
			qsound_adpcm v = chip.adpcm[voice_no];

			int delta;
			byte step;

			if (chip.muteMask != 0 & (1 << (16 + voice_no)) != 0)
			{
				chip.voice_output[16 + voice_no] = 0;
				return;
			}

			if (nibble == 0)
			{
				// Mute voice when it reaches the end address.
				//if (v.cur_addr == v.end_addr)
				if (v.cur_addr == chip.register_map[(voice_no << 2) + 0xcb])
					v.cur_vol = 0;

				// Playback start flag
				//if (v.flag != 0)
					if (chip.register_map[voice_no + 0xd6]  != 0)
					{
						chip.voice_output[16 + voice_no] = 0;
					//v.flag = 0;
					chip.register_map[voice_no + 0xd6] = 0;
					v.step_size = 10;
					//v.cur_vol = v.volume;
					v.cur_vol = (short)chip.register_map[(voice_no << 2) + 0xcd];
					//v.cur_addr = v.start_addr;
					v.cur_addr = chip.register_map[(voice_no << 2) + 0xca];
				}

				// get top nibble
				//step = (byte)(get_sample(chip, v.bank, v.cur_addr) >> 8);
				step = (byte)(get_sample(chip, chip.register_map[(voice_no << 2) + 0xcc], v.cur_addr) >> 8);
			}
			else
			{
				// get bottom nibble
				//step = (byte)(get_sample(chip, v.bank, v.cur_addr++) >> 4);
				step = (byte)(get_sample(chip, chip.register_map[(voice_no << 2) + 0xcc], v.cur_addr++) >> 4);
			}

			// shift with sign extend
			step >>= 4;

			// delta = (0.5 + abs(v.step)) * v.step_size
			delta = ((1 + Math.abs(step << 1)) * v.step_size) >> 1;
			if (step <= 0)
				delta = -delta;
			delta += chip.voice_output[16 + voice_no];
			delta = CLAMP((short)delta, -32768, 32767);

			chip.voice_output[16 + voice_no] = (short)((delta * v.cur_vol) >> 16);

			v.step_size = (short)((adpcm_step_table[8 + step] * v.step_size) >> 6);
			v.step_size = (short)CLAMP(v.step_size, 1, 2000);
		}

		// The echo effect is pretty simple. A moving average filter is used on
		// the output from the delay line to smooth samples over time. 
		private short echo(qsound_chip chip,qsound_echo r, int input)
		{
			// get average of last 2 samples from the delay line
			int new_sample;
			int old_sample = r.delay_line[r.delay_pos];
			int last_sample = r.last_sample;

			r.last_sample = (short)old_sample;
			old_sample = (old_sample + last_sample) >> 1;

			// add current sample to the delay line
			//new_sample = input + ((old_sample * r.feedback) << 2);
			new_sample = input + ((old_sample * chip.register_map[0x93]) << 2);
			r.delay_line[r.delay_pos++] = (short)(new_sample >> 16);

			if (r.delay_pos >= r.length)
				r.delay_pos = 0;

			return (short)old_sample;
		}

		// Process a sample update
		private void state_normal_update(qsound_chip chip)
		{
			int v, ch;
			int echo_input = 0;
			short echo_output;

			chip.ready_flag = (byte) 0x80;

			// recalculate echo length
			if (chip.state == (int)STATE.NORMAL2.v)
				//chip.echo.length = (short)(chip.echo.end_pos - 0x53c);
				chip.echo.length = (short)(chip.register_map[0xd9] - 0x53c);
			else
				//chip.echo.length = (short)(chip.echo.end_pos - 0x554);
				chip.echo.length = (short)(chip.register_map[0xd9] - 0x554);

			chip.echo.length = (short)CLAMP(chip.echo.length, 0, 1024);

			// update PCM voices
			for (v = 0; v < 16; v++)
				chip.voice_output[v] = pcm_update(chip, v, echo_input);

			// update ADPCM voices (one every third sample)
			adpcm_update(chip, chip.state_counter % 3, chip.state_counter / 3);

			echo_output = echo(chip, chip.echo, echo_input);

			// now, we do the magic stuff
			for (ch = 0; ch < 2; ch++)
			{
				// Echo is output on the unfiltered component of the left channel and
				// the filtered component of the right channel.
				int wet = (ch == 1) ? echo_output << 14 : 0;
				int dry = (ch == 0) ? echo_output << 14 : 0;
				int output = 0;

				for (v = 0; v < 19; v++)
				{
					//int pan_index = (int)(chip.voice_pan[v] - 0x110);
					int pan_index = (int)(chip.register_map[v + 0x80] - 0x110);
					if (pan_index > 97)
						pan_index = 97;

					// Apply different volume tables on the dry and wet inputs.
					dry -= (chip.voice_output[v] * chip.pan_tables[ch][(int)PANTBL.DRY.v][pan_index]);
					wet -= (chip.voice_output[v] * chip.pan_tables[ch][(int)PANTBL.WET.v][pan_index]);
				}

				// Saturate accumulated voices
				dry = CLAMP(dry, -0x1fffffff, 0x1fffffff) << 2;
				wet = CLAMP(wet, -0x1fffffff, 0x1fffffff) << 2;

				// Apply FIR filter on 'wet' input
				wet = fir(chip.filter[ch], (short)(wet >> 16));

				// in mode 2, we do this on the 'dry' input too
				if (chip.state == (int)STATE.NORMAL2.v)
					dry = fir(chip.alt_filter[ch], (short)(dry >> 16));

				// output goes through a delay line and attenuation
				output = (delay(chip, false, ch, chip.wet[ch], wet) + delay(chip, true, ch, chip.dry[ch], dry));

				// DSP round function
				output = ((output + 0x2000) & ~0x3fff) >> 14;
				chip._out[ch] = (short)CLAMP(output, -0x7fff, 0x7fff);

				//if (chip.delay_update != 0)
				if (chip.register_map[0xe2] != 0)
				{
					delay_update(chip, false, ch, chip.wet[ch]);
					delay_update(chip, true, ch, chip.dry[ch]);
				}
			}

			//chip.delay_update = 0;
			chip.register_map[0xe2] = 0;

			// after 6 samples, the next state is executed.
			chip.state_counter++;
			if (chip.state_counter > 5)
			{
				chip.state_counter = 0;
				//chip.state = chip.next_state;
				chip.state = chip.register_map[0xe3];
			}
		}

		// Apply the FIR filter used as the Q1 transfer function
		private int fir(qsound_fir f, short input)
		{
			int output = 0, tap = 0;

			for (; tap < (f.tap_count - 1); tap++)
			{
				output -= (f.taps[tap] * f.delay_line[f.delay_pos++]) << 2;

				if (f.delay_pos >= f.tap_count - 1)
					f.delay_pos = 0;
			}

			output -= (f.taps[tap] * input) << 2;

			f.delay_line[f.delay_pos++] = input;
			if (f.delay_pos >= f.tap_count - 1)
				f.delay_pos = 0;

			return output;
		}

		// Apply delay line and component volume
		private int delay(qsound_chip chip, boolean isDry, int ch, qsound_delay d, int input)
		{
			int output;

			d.delay_line[d.write_pos++] = (short)(input >> 16);
			if (d.write_pos >= 51)
				d.write_pos = 0;

			//output = d.delay_line[d.read_pos++] * d.volume;
			output = d.delay_line[d.read_pos++] * chip.register_map[(ch << 1) + (isDry ? 0xe5 : 0xe4)];
			if (d.read_pos >= 51)
				d.read_pos = 0;

			return output;
		}

		// Update the delay read position to match new delay length
		private void delay_update(qsound_chip chip, boolean isDry, int ch, qsound_delay d)
		{
			//short new_read_pos = (short)((d.write_pos - d.delay) % 51);
			short new_read_pos = (short)((d.write_pos - chip.register_map[(ch << 1) + (isDry ? 0xdf : 0xde)]) % 51);
			if (new_read_pos < 0)
				new_read_pos += 51;

			d.read_pos = new_read_pos;
		}
	}
