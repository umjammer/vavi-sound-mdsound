﻿package MDSound.mame;
import java.util.function.BiConsumer;

import MDSound.Common.QuadConsumer;
import MDSound.mame.fm.FM_base;

	public class fm2612
	{
		/*
		**
		** File: fm2612.c -- software implementation of Yamaha YM2612 FM sound generator
		** Split from fm.c to keep 2612 fixes from infecting other OPN chips
		**
		** Copyright Jarek Burczynski (bujar at mame dot net)
		** Copyright Tatsuyuki Satoh , MultiArcadeMachineEmulator development
		**
		** Version 1.5.1 (Genesis Plus GX ym2612.c rev. 368)
		**
		*/

		/*
		** History:
		**
		** 2006~2012  Eke-Eke (Genesis Plus GX):
		** Huge thanks to Nemesis, lot of those fixes came from his tests on Sega Genesis hardware
		** More informations at http://gendev.spritesmind.net/forum/viewtopic.php?t=386
		**
		**  TODO:
		**
		**  - core documentation
		**  - BUSY flag support
		**
		**  CHANGELOG:
		**
		**  - fixed LFO implementation:
		**      .added support for CH3 special mode: fixes various sound effects (birds in Warlock, bug sound in Aladdin...)
		**      .inverted LFO AM waveform: fixes Spider-Man & Venom : Separation Anxiety (intro), California Games (surfing event)
		**      .improved LFO timing accuracy: now updated AFTER sample output, like EG/PG updates, and without any precision loss anymore.
		**  - improved internal timers emulation
		**  - adjusted lowest EG rates increment values
		**  - fixed Attack Rate not being updated in some specific cases (Batman & Robin intro)
		**  - fixed EG behavior when Attack Rate is maximal
		**  - fixed EG behavior when SL=0 (Mega Turrican tracks 03,09...) or/and Key ON occurs at minimal attenuation
		**  - implemented EG output immediate changes on register writes
		**  - fixed YM2612 initial values (after the reset): fixes missing intro in B.O.B
		**  - implemented Detune overflow (Ariel, Comix Zone, Shaq Fu, Spiderman & many other games using GEMS sound engine)
		**  - implemented accurate CSM mode emulation
		**  - implemented accurate SSG-EG emulation (Asterix, Beavis&Butthead, Bubba'n Stix & many other games)
		**  - implemented accurate address/data ports behavior
		**
		** 06-23-2007 Zsolt Vasvari:
		**  - changed the timing not to require the use of floating point calculations
		**
		** 03-08-2003 Jarek Burczynski:
		**  - fixed YM2608 initial values (after the reset)
		**  - fixed flag and irqmask handling (YM2608)
		**  - fixed BUFRDY flag handling (YM2608)
		**
		** 14-06-2003 Jarek Burczynski:
		**  - implemented all of the YM2608 status register flags
		**  - implemented support for external memory read/write via YM2608
		**  - implemented support for deltat memory limit register in YM2608 emulation
		**
		** 22-05-2003 Jarek Burczynski:
		**  - fixed LFO PM calculations (copy&paste bugfix)
		**
		** 08-05-2003 Jarek Burczynski:
		**  - fixed SSG support
		**
		** 22-04-2003 Jarek Burczynski:
		**  - implemented 100% correct LFO generator (verified on real YM2610 and YM2608)
		**
		** 15-04-2003 Jarek Burczynski:
		**  - added support for YM2608's register 0x110 - status mask
		**
		** 01-12-2002 Jarek Burczynski:
		**  - fixed register addressing in YM2608, YM2610, YM2610B chips. (verified on real YM2608)
		**    The addressing patch used for early Neo-Geo games can be removed now.
		**
		** 26-11-2002 Jarek Burczynski, Nicola Salmoria:
		**  - recreated YM2608 ADPCM ROM using data from real YM2608's output which leads to:
		**  - added emulation of YM2608 drums.
		**  - output of YM2608 is two times lower now - same as YM2610 (verified on real YM2608)
		**
		** 16-08-2002 Jarek Burczynski:
		**  - binary exact Envelope Generator (verified on real YM2203);
		**    identical to YM2151
		**  - corrected 'off by one' error in feedback calculations (when feedback is off)
		**  - corrected connection (algorithm) calculation (verified on real YM2203 and YM2610)
		**
		** 18-12-2001 Jarek Burczynski:
		**  - added SSG-EG support (verified on real YM2203)
		**
		** 12-08-2001 Jarek Burczynski:
		**  - corrected sin_tab and tl_tab data (verified on real chip)
		**  - corrected feedback calculations (verified on real chip)
		**  - corrected phase generator calculations (verified on real chip)
		**  - corrected envelope generator calculations (verified on real chip)
		**  - corrected FM volume level (YM2610 and YM2610B).
		**  - changed YMxxxUpdateOne() functions (YM2203, YM2608, YM2610, YM2610B, YM2612) :
		**    this was needed to calculate YM2610 FM channels output correctly.
		**    (Each FM channel is calculated as in other chips, but the output of the channel
		**    gets shifted right by one *before* sending to accumulator. That was impossible to do
		**    with previous implementation).
		**
		** 23-07-2001 Jarek Burczynski, Nicola Salmoria:
		**  - corrected YM2610 ADPCM type A algorithm and tables (verified on real chip)
		**
		** 11-06-2001 Jarek Burczynski:
		**  - corrected end of sample bug in ADPCMA_calc_cha().
		**    Real YM2610 checks for equality between current and end addresses (only 20 LSB bits).
		**
		** 08-12-98 hiro-shi:
		** rename ADPCMA . ADPCMB, ADPCMB . ADPCMA
		** move ROM limit check.(CALC_CH? . 2610Write1/2)
		** test program (ADPCMB_TEST)
		** move ADPCM A/B end check.
		** ADPCMB repeat flag(no check)
		** change ADPCM volume rate (8.16) (32.48).
		**
		** 09-12-98 hiro-shi:
		** change ADPCM volume. (8.16, 48.64)
		** replace ym2610 ch0/3 (YM-2610B)
		** change ADPCM_SHIFT (10.8) missing bank change 0x4000-0xffff.
		** add ADPCM_SHIFT_MASK
		** change ADPCMA_DECODE_MIN/MAX.
		*/




		/************************************************************************/
		/*    comment of hiro-shi(Hiromitsu Shioya)                             */
		/*    YM2610(B) = OPN-B                                                 */
		/*    YM2610  : PSG:3ch FM:4ch ADPCM(18.5KHz):6ch DeltaT ADPCM:1ch      */
		/*    YM2610B : PSG:3ch FM:6ch ADPCM(18.5KHz):6ch DeltaT ADPCM:1ch      */
		/************************************************************************/

		//#include "emu.h"
		//# include <stdlib.h>
		//# include <string.h>	// for memset
		//# include <stddef.h>	// for NULL
		//# include <math.h>
		//# include "mamedef.h"
		//# include "fm.h"

		/* shared function building option */
		private final int BUILD_OPN = (fm.BUILD_YM2203 | fm.BUILD_YM2608 | fm.BUILD_YM2610 | fm.BUILD_YM2610B | fm.BUILD_YM2612 | fm.BUILD_YM3438);
		private final int BUILD_OPN_PRESCALER = (fm.BUILD_YM2203 | fm.BUILD_YM2608);


		/* globals */
		private final int TYPE_SSG = 0x01;    /* SSG support          */
		private final int TYPE_LFOPAN = 0x02;    /* OPN type LFO and PAN */
		private final int TYPE_6CH = 0x04;    /* FM 6CH / 3CH         */
		private final int TYPE_DAC = 0x08;    /* YM2612's DAC device  */
		private final int TYPE_ADPCM = 0x10;    /* two ADPCM units      */
		private final int TYPE_2610 = 0x20;    /* bogus flag to differentiate 2608 from 2610 */


		private final int TYPE_YM2203 = (TYPE_SSG);
		private final int TYPE_YM2608 = (TYPE_SSG | TYPE_LFOPAN | TYPE_6CH | TYPE_ADPCM);
		private final int TYPE_YM2610 = (TYPE_SSG | TYPE_LFOPAN | TYPE_6CH | TYPE_ADPCM | TYPE_2610);
		private final int TYPE_YM2612 = (TYPE_DAC | TYPE_LFOPAN | TYPE_6CH);


		/* globals */
		private final int FREQ_SH = 16;  /* 16.16 fixed point (frequency calculations) */
		private final int EG_SH = 16;  /* 16.16 fixed point (envelope generator timing) */
		private final int LFO_SH = 24;  /*  8.24 fixed point (LFO calculations)       */
		private final int TIMER_SH = 16;  /* 16.16 fixed point (timers calculations)    */

		private final int FREQ_MASK = ((1 << FREQ_SH) - 1);

		private final int MAXOUT = (+32767);
		private final int MINOUT = (-32768);

		/* envelope generator */
		private final int ENV_BITS = 10;
		private final int ENV_LEN = (1 << ENV_BITS);
		private final double ENV_STEP = (128.0 / ENV_LEN);

		private final int MAX_ATT_INDEX = (ENV_LEN - 1); /* 1023 */
		private final int MIN_ATT_INDEX = (0);      /* 0 */

		private final int EG_ATT = 4;
		private final int EG_DEC = 3;
		private final int EG_SUS = 2;
		private final int EG_REL = 1;
		private final int EG_OFF = 0;

		/* operator unit */
		private final static int SIN_BITS = 10;
		private final static int SIN_LEN = (1 << SIN_BITS);
		private final int SIN_MASK = (SIN_LEN - 1);

		private final static int TL_RES_LEN = (256); /* 8 bits addressing (real chip) */

		/*  TL_TAB_LEN is calculated as:
		*   13 - sinus amplitude bits     (Y axis)
		*   2  - sinus sign bit           (Y axis)
		*   TL_RES_LEN - sinus resolution (X axis)
		*/
		private final static int TL_TAB_LEN = (13 * 2 * TL_RES_LEN);
		private static int[] tl_tab = new int[TL_TAB_LEN];

		private final int ENV_QUIET = (TL_TAB_LEN >> 3);

		/* sin waveform table in 'decibel' scale */
		private static int[] sin_tab = new int[SIN_LEN];

		/* sustain level table (3dB per step) */
		/* bit0, bit1, bit2, bit3, bit4, bit5, bit6 */
		/* 1,    2,    4,    8,    16,   32,   64   (value)*/
		/* 0.75, 1.5,  3,    6,    12,   24,   48   (dB)*/

		/* 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)*/
		/* attenuation value (10 bits) = (SL << 2) << 3 */
		private int SC(int db) { return (int)(db * (4.0 / ENV_STEP)); }
		private int[] sl_table = new int[16];
		//SC( 0),SC( 1),SC( 2),SC(3 ),SC(4 ),SC(5 ),SC(6 ),SC( 7),
		//SC( 8),SC( 9),SC(10),SC(11),SC(12),SC(13),SC(14),SC(31)
		private void initSl_table()
		{
			for (int i = 0; i < 16; i++) sl_table[i] = SC(i == 15 ? 31 : i);
		}


		private final int RATE_STEPS = 8;
		private byte[] eg_inc = new byte[]
		{
/*cycle:0 1  2 3  4 5  6 7*/

/* 0 */ 0,1, 0,1, 0,1, 0,1, /* rates 00..11 0 (increment by 0 or 1) */
/* 1 */ 0,1, 0,1, 1,1, 0,1, /* rates 00..11 1 */
/* 2 */ 0,1, 1,1, 0,1, 1,1, /* rates 00..11 2 */
/* 3 */ 0,1, 1,1, 1,1, 1,1, /* rates 00..11 3 */

/* 4 */ 1,1, 1,1, 1,1, 1,1, /* rate 12 0 (increment by 1) */
/* 5 */ 1,1, 1,2, 1,1, 1,2, /* rate 12 1 */
/* 6 */ 1,2, 1,2, 1,2, 1,2, /* rate 12 2 */
/* 7 */ 1,2, 2,2, 1,2, 2,2, /* rate 12 3 */

/* 8 */ 2,2, 2,2, 2,2, 2,2, /* rate 13 0 (increment by 2) */
/* 9 */ 2,2, 2,4, 2,2, 2,4, /* rate 13 1 */
/*10 */ 2,4, 2,4, 2,4, 2,4, /* rate 13 2 */
/*11 */ 2,4, 4,4, 2,4, 4,4, /* rate 13 3 */

/*12 */ 4,4, 4,4, 4,4, 4,4, /* rate 14 0 (increment by 4) */
/*13 */ 4,4, 4,8, 4,4, 4,8, /* rate 14 1 */
/*14 */ 4,8, 4,8, 4,8, 4,8, /* rate 14 2 */
/*15 */ 4,8, 8,8, 4,8, 8,8, /* rate 14 3 */

/*16 */ 8,8, 8,8, 8,8, 8,8, /* rates 15 0, 15 1, 15 2, 15 3 (increment by 8) */
/*17 */ 16,16,16,16,16,16,16,16, /* rates 15 2, 15 3 for attack */
/*18 */ 0,0, 0,0, 0,0, 0,0, /* infinity rates for attack and decay(s) */
		};


		private byte O(int a) { return (byte)(a * RATE_STEPS); }

		/*note that there is no O(17) in this table - it's directly in the code */
		private byte[] eg_rate_select2612 = null;   /* Envelope Generator rates (32 + 64 rates + 32 RKS) */
		private void initEg_rate_select()
		{
			eg_rate_select2612 = new byte[]
			{
				/* 32 infinite time rates (same as Rate 0) */
				O(18),O(18),O(18),O(18),O(18),O(18),O(18),O(18),
				O(18),O(18),O(18),O(18),O(18),O(18),O(18),O(18),
				O(18),O(18),O(18),O(18),O(18),O(18),O(18),O(18),
				O(18),O(18),O(18),O(18),O(18),O(18),O(18),O(18),

				/* rates 00-11 */
				/*
				O( 0),O( 1),O( 2),O( 3),
				O( 0),O( 1),O( 2),O( 3),
				*/
				O(18),O(18),O( 0),O( 0),
				O( 0),O( 0),O( 2),O( 2),   // Nemesis's tests
				
				O( 0),O( 1),O( 2),O( 3),
				O( 0),O( 1),O( 2),O( 3),
				O( 0),O( 1),O( 2),O( 3),
				O( 0),O( 1),O( 2),O( 3),
				O( 0),O( 1),O( 2),O( 3),
				O( 0),O( 1),O( 2),O( 3),
				O( 0),O( 1),O( 2),O( 3),
				O( 0),O( 1),O( 2),O( 3),
				O( 0),O( 1),O( 2),O( 3),
				O( 0),O( 1),O( 2),O( 3),
				
				/* rate 12 */
				O( 4),O( 5),O( 6),O( 7),
				
				/* rate 13 */
				O( 8),O( 9),O(10),O(11),
				
				/* rate 14 */
				O(12),O(13),O(14),O(15),
				
				/* rate 15 */
				O(16),O(16),O(16),O(16),
				
				/* 32 dummy rates (same as 15 3) */
				O(16),O(16),O(16),O(16),O(16),O(16),O(16),O(16),
				O(16),O(16),O(16),O(16),O(16),O(16),O(16),O(16),
				O(16),O(16),O(16),O(16),O(16),O(16),O(16),O(16),
				O(16),O(16),O(16),O(16),O(16),O(16),O(16),O(16)

			};
		}

		/*rate  0,    1,    2,   3,   4,   5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15*/
		/*shift 11,   10,   9,   8,   7,   6,  5,  4,  3,  2, 1,  0,  0,  0,  0,  0 */
		/*mask  2047, 1023, 511, 255, 127, 63, 31, 15, 7,  3, 1,  0,  0,  0,  0,  0 */

		private byte O2(int a) { return (byte)(a * 1); }
		private byte[] eg_rate_shift = null;
		private void initEg_rate_shift()
		{
			eg_rate_shift = new byte[]
			{  /* Envelope Generator counter shifts (32 + 64 rates + 32 RKS) */
				/* 32 infinite time rates */
				/* O(0),O(0),O(0),O(0),O(0),O(0),O(0),O(0),
				O(0),O(0),O(0),O(0),O(0),O(0),O(0),O(0),
				O(0),O(0),O(0),O(0),O(0),O(0),O(0),O(0),
				O(0),O(0),O(0),O(0),O(0),O(0),O(0),O(0), */
				
				/* fixed (should be the same as rate 0, even if it makes no difference since increment value is 0 for these rates) */
				O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),
				O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),
				O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),
				O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),O2(11),
				
				/* rates 00-11 */
				O2(11),O2(11),O2(11),O2(11),
				O2(10),O2(10),O2(10),O2(10),
				O2( 9),O2( 9),O2( 9),O2( 9),
				O2( 8),O2( 8),O2( 8),O2( 8),
				O2( 7),O2( 7),O2( 7),O2( 7),
				O2( 6),O2( 6),O2( 6),O2( 6),
				O2( 5),O2( 5),O2( 5),O2( 5),
				O2( 4),O2( 4),O2( 4),O2( 4),
				O2( 3),O2( 3),O2( 3),O2( 3),
				O2( 2),O2( 2),O2( 2),O2( 2),
				O2( 1),O2( 1),O2( 1),O2( 1),
				O2( 0),O2( 0),O2( 0),O2( 0),
				
				/* rate 12 */
				O2( 0),O2( 0),O2( 0),O2( 0),
				
				/* rate 13 */
				O2( 0),O2( 0),O2( 0),O2( 0),
				
				/* rate 14 */
				O2( 0),O2( 0),O2( 0),O2( 0),
				
				/* rate 15 */
				O2( 0),O2( 0),O2( 0),O2( 0),
				
				/* 32 dummy rates (same as 15 3) */
				O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),
				O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),
				O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),
				O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0),O2( 0)

			};
		}

		private byte[] dt_tab = new byte[]{
		/* this is YM2151 and YM2612 phase increment data (in 10.10 fixed point format)*/
		/* FD=0 */
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		/* FD=1 */
			0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2,
			2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7, 8, 8, 8, 8,
		/* FD=2 */
			1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5,
			5, 6, 6, 7, 8, 8, 9,10,11,12,13,14,16,16,16,16,
		/* FD=3 */
			2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7,
			8 , 8, 9,10,11,12,13,14,16,17,19,20,22,22,22,22
		};


		/* OPN key frequency number . key code follow table */
		/* fnum higher 4bit . keycode lower 2bit */
		private byte[] opn_fktable = new byte[] { 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 3, 3, 3, 3, 3 };


		/* 8 LFO speed parameters */
		/* each value represents number of samples that one LFO level will last for */
		private int[] lfo_samples_per_step = new int[] { 108, 77, 71, 67, 62, 44, 8, 5 };



		/*There are 4 different LFO AM depths available, they are:
		  0 dB, 1.4 dB, 5.9 dB, 11.8 dB
		  Here is how it is generated (in EG steps):

		  11.8 dB = 0, 2, 4, 6, 8, 10,12,14,16...126,126,124,122,120,118,....4,2,0
		   5.9 dB = 0, 1, 2, 3, 4, 5, 6, 7, 8....63, 63, 62, 61, 60, 59,.....2,1,0
		   1.4 dB = 0, 0, 0, 0, 1, 1, 1, 1, 2,...15, 15, 15, 15, 14, 14,.....0,0,0

		  (1.4 dB is losing precision as you can see)

		  It's implemented as generator from 0..126 with step 2 then a shift
		  right N times, where N is:
			8 for 0 dB
			3 for 1.4 dB
			1 for 5.9 dB
			0 for 11.8 dB
		*/
		private byte[] lfo_ams_depth_shift = new byte[] { 8, 3, 1, 0 };



		/*There are 8 different LFO PM depths available, they are:
		  0, 3.4, 6.7, 10, 14, 20, 40, 80 (cents)

		  Modulation level at each depth depends on F-NUMBER bits: 4,5,6,7,8,9,10
		  (bits 8,9,10 = FNUM MSB from OCT/FNUM register)

		  Here we store only first quarter (positive one) of full waveform.
		  Full table (lfo_pm_table) containing all 128 waveforms is build
		  at run (init) time.

		  One value in table below represents 4 (four) basic LFO steps
		  (1 PM step = 4 AM steps).

		  For example:
		   at LFO SPEED=0 (which is 108 samples per basic LFO step)
		   one value from "lfo_pm_output" table lasts for 432 consecutive
		   samples (4*108=432) and one full LFO waveform cycle lasts for 13824
		   samples (32*432=13824; 32 because we store only a quarter of whole
					waveform in the table below)
		*/
		private byte[][] lfo_pm_output = new byte[][]{ /* 7 bits meaningful (of F-NUMBER), 8 LFO output levels per one depth (of 32), 8 LFO depths */
		/* FNUM BIT 4: 000 0001xxxx */
		/* DEPTH 0 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 1 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 2 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 3 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 4 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 5 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 6 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 7 */ new byte[]{0,   0,   0,   0,   1,   1,   1,   1},
		
		/* FNUM BIT 5: 000 0010xxxx */
		/* DEPTH 0 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 1 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 2 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 3 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 4 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 5 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 6 */ new byte[]{0,   0,   0,   0,   1,   1,   1,   1},
		/* DEPTH 7 */ new byte[]{0,   0,   1,   1,   2,   2,   2,   3},
		
		/* FNUM BIT 6: 000 0100xxxx */
		/* DEPTH 0 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 1 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 2 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 3 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 4 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   1},
		/* DEPTH 5 */ new byte[]{0,   0,   0,   0,   1,   1,   1,   1},
		/* DEPTH 6 */ new byte[]{0,   0,   1,   1,   2,   2,   2,   3},
		/* DEPTH 7 */ new byte[]{0,   0,   2,   3,   4,   4,   5,   6},
		
		/* FNUM BIT 7: 000 1000xxxx */
		/* DEPTH 0 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 1 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 2 */ new byte[]{0,   0,   0,   0,   0,   0,   1,   1},
		/* DEPTH 3 */ new byte[]{0,   0,   0,   0,   1,   1,   1,   1},
		/* DEPTH 4 */ new byte[]{0,   0,   0,   1,   1,   1,   1,   2},
		/* DEPTH 5 */ new byte[]{0,   0,   1,   1,   2,   2,   2,   3},
		/* DEPTH 6 */ new byte[]{0,   0,   2,   3,   4,   4,   5,   6},
		/* DEPTH 7 */ new byte[]{0,   0,   4,   6,   8,   8, 0xa, 0xc},
		
		/* FNUM BIT 8: 001 0000xxxx */
		/* DEPTH 0 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 1 */ new byte[]{0,   0,   0,   0,   1,   1,   1,   1},
		/* DEPTH 2 */ new byte[]{0,   0,   0,   1,   1,   1,   2,   2},
		/* DEPTH 3 */ new byte[]{0,   0,   1,   1,   2,   2,   3,   3},
		/* DEPTH 4 */ new byte[]{0,   0,   1,   2,   2,   2,   3,   4},
		/* DEPTH 5 */ new byte[]{0,   0,   2,   3,   4,   4,   5,   6},
		/* DEPTH 6 */ new byte[]{0,   0,   4,   6,   8,   8, 0xa, 0xc},
		/* DEPTH 7 */ new byte[]{0,   0,   8, 0xc,0x10,0x10,0x14,0x18},
		
		/* FNUM BIT 9: 010 0000xxxx */
		/* DEPTH 0 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 1 */ new byte[]{0,   0,   0,   0,   2,   2,   2,   2},
		/* DEPTH 2 */ new byte[]{0,   0,   0,   2,   2,   2,   4,   4},
		/* DEPTH 3 */ new byte[]{0,   0,   2,   2,   4,   4,   6,   6},
		/* DEPTH 4 */ new byte[]{0,   0,   2,   4,   4,   4,   6,   8},
		/* DEPTH 5 */ new byte[]{0,   0,   4,   6,   8,   8, 0xa, 0xc},
		/* DEPTH 6 */ new byte[]{0,   0,   8, 0xc,0x10,0x10,0x14,0x18},
		/* DEPTH 7 */ new byte[]{0,   0,0x10,0x18,0x20,0x20,0x28,0x30},
		
		/* FNUM BIT10: 100 0000xxxx */
		/* DEPTH 0 */ new byte[]{0,   0,   0,   0,   0,   0,   0,   0},
		/* DEPTH 1 */ new byte[]{0,   0,   0,   0,   4,   4,   4,   4},
		/* DEPTH 2 */ new byte[]{0,   0,   0,   4,   4,   4,   8,   8},
		/* DEPTH 3 */ new byte[]{0,   0,   4,   4,   8,   8, 0xc, 0xc},
		/* DEPTH 4 */ new byte[]{0,   0,   4,   8,   8,   8, 0xc,0x10},
		/* DEPTH 5 */ new byte[]{0,   0,   8, 0xc,0x10,0x10,0x14,0x18},
		/* DEPTH 6 */ new byte[]{0,   0,0x10,0x18,0x20,0x20,0x28,0x30},
		/* DEPTH 7 */ new byte[]{0,   0,0x20,0x30,0x40,0x40,0x50,0x60},

		};

		/* all 128 LFO PM waveforms */
		private static int[] lfo_pm_table = new int[128 * 8 * 32]; /* 128 combinations of 7 bits meaningful (of F-NUMBER), 8 LFO depths, 32 LFO output levels per one depth */

		/* register number to channel number , slot offset */
		
		private byte OPN_CHAN(int N) { return (byte)(N & 3); }
		
		private int OPN_SLOT(int N) { return ((N >> 2) & 3); }

		/* slot number */
		private final int SLOT1 = 0;
		private final int SLOT2 = 2;
		private final int SLOT3 = 1;
		private final int SLOT4 = 3;

		/* bit0 = Right enable , bit1 = Left enable */
		private final int OUTD_RIGHT = 1;
		private final int OUTD_LEFT = 2;
		private final int OUTD_CENTER = 3;


		/* save output as raw 16-bit sample */
		/* // #define SAVE_SAMPLE */

		//# ifdef SAVE_SAMPLE
		//		static FILE* sample[1];
		//	// #if 1	/*save to MONO file */
		//		// #define SAVE_ALL_CHANNELS \
		//		{	signed int pom = lt; \
		//			fputc((unsigned short)pom&0xff,sample[0]); \
		//			fputc(((unsigned short)pom>>8)&0xff,sample[0]); \
		//		}
		//	// #else	/*save to STEREO file */
		//		// #define SAVE_ALL_CHANNELS \
		//		{	signed int pom = lt; \
		//			fputc((unsigned short)pom&0xff,sample[0]); \
		//			fputc(((unsigned short)pom>>8)&0xff,sample[0]); \
		//			pom = rt; \
		//			fputc((unsigned short)pom&0xff,sample[0]); \
		//			fputc(((unsigned short)pom>>8)&0xff,sample[0]); \
		//		}
		//#endif
		//#endif


		/* struct describing a single operator (SLOT) */
		public static class FM_SLOT
		{

			public int[] DT;      /* detune          :dt_tab[DT] */
			public byte KSR;      /* key scale rate  :3-KSR */
			public int ar;          /* attack rate  */
			public int d1r;     /* decay rate   */
			public int d2r;     /* sustain rate */
			public int rr;          /* release rate */
			public byte ksr;      /* key scale rate  :kcode>>(3-KSR) */
			public int mul;     /* multiple        :ML_TABLE[ML] */

			/* Phase Generator */
			public int phase;       /* phase counter */
			public int Incr;     /* phase step */

			/* Envelope Generator */
			public byte state;        /* phase type */
			public int tl;          /* total level: TL << 3 */
			public int volume;       /* envelope counter */
			public int sl;          /* sustain level:sl_table[SL] */
			public int vol_out; /* current output from EG circuit (without AM from LFO) */

			public byte eg_sh_ar; /*  (attack state) */
			public byte eg_sel_ar;    /*  (attack state) */
			public byte eg_sh_d1r;    /*  (decay state) */
			public byte eg_sel_d1r;   /*  (decay state) */
			public byte eg_sh_d2r;    /*  (sustain state) */
			public byte eg_sel_d2r;   /*  (sustain state) */
			public byte eg_sh_rr; /*  (release state) */
			public byte eg_sel_rr;    /*  (release state) */

			public byte ssg;      /* SSG-EG waveform */
			public byte ssgn;     /* SSG-EG negated output */

			public byte key;      /* 0=last key was KEY OFF, 1=KEY ON */

			/* LFO */
			public int AMmask;      /* AM enable flag */

		}

		public static class FM_CH
		{

			public FM_SLOT[] SLOT = new FM_SLOT[] {
				new FM_SLOT(), new FM_SLOT(), new FM_SLOT(), new FM_SLOT()
			};    /* four SLOTs (operators) */

			public byte ALGO;     /* algorithm */
			public byte FB;           /* feedback shift */
			public int[] op1_out = new int[2];   /* op1 output for feedback */

			public FM_OP connect1;    /* SLOT1 output pointer */
			public FM_OP connect3;    /* SLOT3 output pointer */
			public FM_OP connect2;    /* SLOT2 output pointer */
			public FM_OP connect4;    /* SLOT4 output pointer */
			public FM_OP mem_connect;/* where to put the delayed sample (MEM) */

			public int mem_value;    /* delayed sample (MEM) value */

			public int pms;      /* channel PMS */
			public byte ams;      /* channel AMS */

			public int fc;          /* fnum,blk:adjusted to sample rate */
			public byte kcode;        /* key code:                        */
			public int block_fnum;  /* current blk/fnum value for this slot (can be different betweeen slots of one channel in 3slot mode) */
			public byte Muted;
		}


		public static class FM_ST
		{
			//running_device *device;
			public YM2612 param;                /* this chip parameter  */
			public double freqbase;            /* frequency base       */
			public int timer_prescaler;    /* timer prescaler      */
			public byte irq;              /* interrupt level      */
			public byte irqmask;          /* irq mask             */
			//#if FM_BUSY_FLAG_SUPPORT
			//	TIME_TYPE	busy_expiry_time;	/* expiry time of the busy status */
			//#endif
			public int clock;               /* master clock  (Hz)   */
			public int rate;                /* sampling rate (Hz)   */
			public byte address;          /* address register     */
			public byte status;               /* status flag          */
			public int mode;                /* mode  CSM / 3SLOT    */
			public byte fn_h;             /* freq latch           */
			public byte prescaler_sel;        /* prescaler selector   */
			public int TA;                   /* timer a              */
			public int TAC;              /* timer a counter      */
			public byte TB;                   /* timer b              */
			public int TBC;              /* timer b counter      */
			/* local time tables */
			public int[][] dt_tab = new int[][] {
				new int[32], new int[32], new int[32], new int[32],
				new int[32], new int[32], new int[32], new int[32] };        /* DeTune table         */
			/* Extention Timer and IRQ handler */
			public interface dlgFM_TIMERHANDLER extends QuadConsumer<Object, Integer, Integer, Integer>{};
			public dlgFM_TIMERHANDLER timer_handler;
			public interface dlgFM_IRQHANDLER extends BiConsumer<FM_base, Integer>{};
			public dlgFM_IRQHANDLER IRQ_Handler;
			public fm._ssg_callbacks SSG;
		}


		/***********************************************************/
		/* OPN unit                                                */
		/***********************************************************/

		/* OPN 3slot struct */
		public class FM_3SLOT
		{
			public int[] fc = new int[3];           /* fnum3,blk3: calculated */
			public byte fn_h;         /* freq3 latch */
			public byte[] kcode = new byte[3];     /* key code */
			public int[] block_fnum = new int[3]; /* current fnum value for this slot (can be different betweeen slots of one channel in 3slot mode) */
			public byte key_csm;        /* CSM mode Key-ON flag */
		}

		/* OPN/A/B common state */
		public class FM_OPN
		{
			public byte type;         /* chip type */
			public FM_ST ST=new FM_ST();               /* general state */
			public FM_3SLOT SL3=new FM_3SLOT();           /* 3 slot mode state */
			public FM_CH[] P_CH;            /* pointer of CH */
			public int[] pan = new int[6 * 2];    /* fm channels output masks (0xffffffff = enable) */

			public int eg_cnt;          /* Global envelope generator counter */
			public int eg_timer;        /* Global envelope generator counter works at frequency = chipclock/64/3 */
			public int eg_timer_add;    /* step of eg_timer */
			public int eg_timer_overflow;/* envelope generator timer overlfows every 3 samples (on real chip) */


			/* there are 2048 FNUMs that can be generated using FNUM/BLK registers
			   but LFO works with one more bit of a precision so we really need 4096 elements */
			public int[] fn_table = new int[4096];  /* fnumber.increment counter */
			public int fn_max;    /* maximal phase increment (used for phase overflow) */

			/* LFO */
			public byte lfo_cnt;            /* current LFO phase (of 128) */
			public int lfo_timer;          /* current LFO phase runs at LFO frequency */
			public int lfo_timer_add;      /* step of lfo_timer */
			public int lfo_timer_overflow; /* LFO timer overflows every N samples (depends on LFO frequency) */
			public int LFO_AM;             /* current LFO AM step */
			public int LFO_PM;             /* current LFO PM step */

			public FM_OP m2=new FM_OP(),
				c1 = new FM_OP(),
				c2 = new FM_OP();       /* Phase Modulation input for operators 2,3,4 */
			public FM_OP mem = new FM_OP();          /* one sample delay memory */
			public FM_OP[] out_fm = new FM_OP[] { new FM_OP(), new FM_OP(), new FM_OP(), new FM_OP(), new FM_OP(), new FM_OP() };        /* outputs of working channels */

		}

		/* here's the virtual YM2612 */
		public static class YM2612 extends fm.FM_base
		{
			public byte[] REGS = new byte[512];            /* registers            */
			public FM_OPN OPN;             /* OPN state            */
			public FM_CH[] CH = new FM_CH[] { 
				new FM_CH(), new FM_CH(), new FM_CH(), 
				new FM_CH(), new FM_CH(), new FM_CH() 
			};                /* channel state        */
			public byte addr_A1;          /* address line A1      */

			/* dac output (YM2612) */
			//int			dacen;
			public byte dacen;
			public byte dac_test;
			public int dacout;
			public byte MuteDAC;

			public byte WaveOutMode;
			public int WaveL;
			public int WaveR;
		}

		/* log output level */
		private final int LOG_ERR = 3;      /* ERROR       */
		private final int LOG_WAR = 2;  /* WARNING     */
		private final int LOG_INF = 1; /* INFORMATION */
		private final int LOG_LEVEL = LOG_INF;

		//# ifndef __RAINE__
		//#define LOG(n,x) do { if( (n)>=LOG_LEVEL ) logerror x; } while (0)
		//#endif

		/* limitter */
		
		private void Limit(int val, int max, int min)
		{
			if (val > max) val = max;
			else if (val < min) val = min;
		}

		private byte IsVGMInit;
		private byte PseudoSt = 0x00;
		/*#include <stdio.h>
		static FILE* hFile;
		static (int)32 FileSample;*/

		/* status set and IRQ handling */
		//INLINE
		
		private void FM_STATUS_SET(FM_ST ST, int flag)
		{
			/* set status flag */
			ST.status |= (byte)flag;
			if (ST.irq == 0 && ((ST.status & ST.irqmask) != 0))
			{
				ST.irq = 1;
				/* callback user interrupt handler (IRQ is OFF to ON) */
				if (ST.IRQ_Handler != null) ST.IRQ_Handler.accept(ST.param, 1);
			}
		}

		/* status reset and IRQ handling */
		//INLINE
		
		private void FM_STATUS_RESET(FM_ST ST, int flag)
		{
			/* reset status flag */
			ST.status &= (byte)~flag;
			if ((ST.irq != 0) && ((ST.status & ST.irqmask) == 0))
			{
				ST.irq = 0;
				/* callback user interrupt handler (IRQ is ON to OFF) */
				if (ST.IRQ_Handler != null) ST.IRQ_Handler.accept(ST.param, 0);
			}
		}

		/* IRQ mask set */
		//INLINE
		
		private void FM_IRQMASK_SET(FM_ST ST, int flag)
		{
			ST.irqmask = (byte)flag;
			/* IRQ handling check */
			FM_STATUS_SET(ST, 0);
			FM_STATUS_RESET(ST, 0);
		}

		//INLINE
		
		private void FM_KEYON(FM_OPN OPN, FM_CH CH, int s)
		{
			FM_SLOT SLOT = CH.SLOT[s];

			// Note by Valley Bell:
			//  I assume that the CSM mode shouldn't affect channels
			//  other than FM3, so I added a check for it here.
			if (SLOT.key == 0 && (OPN.SL3.key_csm == 0 || CH == OPN.P_CH[3]))
			{
				/* restart Phase Generator */
				SLOT.phase = 0;

				/* reset SSG-EG inversion flag */
				SLOT.ssgn = 0;

				if ((SLOT.ar + SLOT.ksr) < 94 /*32+62*/)
				{
					SLOT.state = (byte)((SLOT.volume <= MIN_ATT_INDEX) ? ((SLOT.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC) : EG_ATT);
				}
				else
				{
					/* force attenuation level to 0 */
					SLOT.volume = MIN_ATT_INDEX;

					/* directly switch to Decay (or Sustain) */
					SLOT.state = (SLOT.sl == MIN_ATT_INDEX) ? (byte)EG_SUS : (byte)EG_DEC;
				}

				/* recalculate EG output */
				if ((SLOT.ssg & 0x08) != 0 && (SLOT.ssgn ^ (SLOT.ssg & 0x04)) != 0)
					SLOT.vol_out = ((int)(0x200 - SLOT.volume) & MAX_ATT_INDEX) + SLOT.tl;
				else
					SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
			}

			SLOT.key = 1;

			//Log.WriteLine(LogLevel.TRACE, String.format("vol_out:{0}", SLOT.vol_out));
		}

		//INLINE
		
		private void FM_KEYOFF(FM_OPN OPN, FM_CH CH, int s)
		{
			FM_SLOT SLOT = CH.SLOT[s];

			if (SLOT.key != 0 && (OPN.SL3.key_csm == 0 || CH == OPN.P_CH[3]))
			{
				if (IsVGMInit != 0)  // workaround for VGMs trimmed with VGMTool
				{
					SLOT.state = EG_OFF;
					SLOT.volume = MAX_ATT_INDEX;
					SLOT.vol_out = MAX_ATT_INDEX;
				}
				else if (SLOT.state > EG_REL)
				{
					SLOT.state = EG_REL; /* phase . Release */

					/* SSG-EG specific update */
					if ((SLOT.ssg & 0x08) != 0)
					{
						/* convert EG attenuation level */
						if ((SLOT.ssgn ^ (SLOT.ssg & 0x04)) != 0)
							SLOT.volume = (0x200 - SLOT.volume);

						/* force EG attenuation level */
						if (SLOT.volume >= 0x200)
						{
							SLOT.volume = MAX_ATT_INDEX;
							SLOT.state = EG_OFF;
						}

						/* recalculate EG output */
						SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
					}
				}
			}

			SLOT.key = 0;
		}

		//INLINE
		
		private void FM_KEYON_CSM(FM_OPN OPN, FM_CH CH, int s)
		{
			FM_SLOT SLOT = CH.SLOT[s];

			if (SLOT.key == 0 && OPN.SL3.key_csm == 0)
			{
				/* restart Phase Generator */
				SLOT.phase = 0;

				/* reset SSG-EG inversion flag */
				SLOT.ssgn = 0;

				if ((SLOT.ar + SLOT.ksr) < 94 /*32+62*/)
				{
					SLOT.state = (byte)((SLOT.volume <= MIN_ATT_INDEX) ? ((SLOT.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC) : EG_ATT);
				}
				else
				{
					/* force attenuation level to 0 */
					SLOT.volume = MIN_ATT_INDEX;

					/* directly switch to Decay (or Sustain) */
					SLOT.state = (byte)((SLOT.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC);
				}

				/* recalculate EG output */
				if ((SLOT.ssg & 0x08) != 0 && (SLOT.ssgn ^ (SLOT.ssg & 0x04)) != 0)
					SLOT.vol_out = ((int)(0x200 - SLOT.volume) & MAX_ATT_INDEX) + SLOT.tl;
				else
					SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
			}
		}

		//INLINE
		
		private void FM_KEYOFF_CSM(FM_CH CH, int s)
		{
			FM_SLOT SLOT = CH.SLOT[s];
			if (SLOT.key == 0)
			{
				if (IsVGMInit != 0)
				{
					SLOT.state = EG_OFF;
					SLOT.volume = MAX_ATT_INDEX;
					SLOT.vol_out = MAX_ATT_INDEX;
				}
				else if (SLOT.state > EG_REL)
				{
					SLOT.state = EG_REL; /* phase . Release */

					/* SSG-EG specific update */
					if ((SLOT.ssg & 0x08) != 0)
					{
						/* convert EG attenuation level */
						if ((SLOT.ssgn ^ (SLOT.ssg & 0x04)) != 0)
							SLOT.volume = (0x200 - SLOT.volume);

						/* force EG attenuation level */
						if (SLOT.volume >= 0x200)
						{
							SLOT.volume = MAX_ATT_INDEX;
							SLOT.state = EG_OFF;
						}

						/* recalculate EG output */
						SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
					}
				}
			}
		}

		/* OPN Mode Register Write */
		//INLINE
		
		private void set_timers(FM_OPN OPN, FM_ST ST, fm.FM_base n, int v)
		{
			/* b7 = CSM MODE */
			/* b6 = 3 slot mode */
			/* b5 = reset b */
			/* b4 = reset a */
			/* b3 = timer enable b */
			/* b2 = timer enable a */
			/* b1 = load b */
			/* b0 = load a */

			if (((OPN.ST.mode ^ v) & 0xC0) != 0)
			{
				/* phase increment need to be recalculated */
				OPN.P_CH[2].SLOT[SLOT1].Incr = -1;

				/* CSM mode disabled and CSM key ON active*/
				if (((v & 0xC0) != 0x80) && OPN.SL3.key_csm != 0)
				{
					/* CSM Mode Key OFF (verified by Nemesis on real hardware) */
					FM_KEYOFF_CSM(OPN.P_CH[2], SLOT1);
					FM_KEYOFF_CSM(OPN.P_CH[2], SLOT2);
					FM_KEYOFF_CSM(OPN.P_CH[2], SLOT3);
					FM_KEYOFF_CSM(OPN.P_CH[2], SLOT4);
					OPN.SL3.key_csm = 0;
				}
			}

			// reset Timer b flag
			if ((v & 0x20) != 0)
				FM_STATUS_RESET(ST, 0x02);
			// reset Timer a flag
			if ((v & 0x10) != 0)
				FM_STATUS_RESET(ST, 0x01);
			// load b
			if ((v & 2) != 0 && (ST.mode & 2) == 0)
			{
				ST.TBC = (256 - ST.TB) << 4;
				/* External timer handler */
				if (ST.timer_handler != null) ST.timer_handler.accept(n, 1, ST.TBC * ST.timer_prescaler, (int)ST.clock);
			}
			// load a
			if ((v & 1) != 0 && (ST.mode & 1) == 0)
			{
				ST.TAC = (1024 - ST.TA);
				/* External timer handler */
				if (ST.timer_handler != null) ST.timer_handler.accept(n, 0, ST.TAC * ST.timer_prescaler, (int)ST.clock);
				ST.TAC *= 4096;
			}

			ST.mode = (int)v;
		}


		/* Timer A Overflow */
		private void TimerAOver(FM_ST ST)
		{
			/* set status (if enabled) */
			if ((ST.mode & 0x04) != 0) FM_STATUS_SET(ST, 0x01);
			/* clear or reload the counter */
			ST.TAC = (1024 - ST.TA);
			if (ST.timer_handler != null) ST.timer_handler.accept(ST.param, 0, ST.TAC * ST.timer_prescaler, (int)ST.clock);
			ST.TAC *= 4096;
		}

		/* Timer B Overflow */
		private void TimerBOver(FM_ST ST)
		{
			/* set status (if enabled) */
			if ((ST.mode & 0x08) != 0) FM_STATUS_SET(ST, 0x02);
			/* clear or reload the counter */
			ST.TBC = (256 - ST.TB) << 4;
			if (ST.timer_handler != null) ST.timer_handler.accept(ST.param, 1, ST.TBC * ST.timer_prescaler, (int)ST.clock);
		}


		//#if FM_INTERNAL_TIMER
		/* ----- internal timer mode , update timer */
		// Valley Bell: defines fixed

		/* ---------- calculate timer A ---------- */
		private void INTERNAL_TIMER_A(FM_OPN OPN, FM_ST ST, FM_CH CSM_CH)
		{
			if (ST.TAC != 0 && (ST.timer_handler == null))
				if ((ST.TAC -= (int)(ST.freqbase * 4096)) <= 0)
				{
					TimerAOver(ST);
					/* CSM mode total level latch and auto key on */
					if ((ST.mode & 0x80) != 0)
						CSMKeyControll(OPN, CSM_CH);
				}
		}

		/* ---------- calculate timer B ---------- */
		private void INTERNAL_TIMER_B(FM_ST ST, int step)
		{
			if (ST.TBC != 0 && ST.timer_handler == null)
				if ((ST.TBC -= (int)(ST.freqbase * 4096 * step)) <= 0)
					TimerBOver(ST);
		}

		//#else /* FM_INTERNAL_TIMER */
		/* external timer mode */
		//#define INTERNAL_TIMER_A(ST,CSM_CH)
		//#define INTERNAL_TIMER_B(ST,step)
		//#endif /* FM_INTERNAL_TIMER */



		//#if FM_BUSY_FLAG_SUPPORT
		//#define FM_BUSY_CLEAR(ST) ((ST).busy_expiry_time = UNDEFINED_TIME)
		//INLINE (int)8 FM_STATUS_FLAG(FM_ST *ST)
		//{
		//	if( COMPARE_TIMES(ST.busy_expiry_time, UNDEFINED_TIME) != 0 )
		//	{
		//		if (COMPARE_TIMES(ST.busy_expiry_time, FM_GET_TIME_NOW(ST.device.machine)) > 0)
		//			return ST.status | 0x80;	/* with busy */
		//		/* expire */
		//		FM_BUSY_CLEAR(ST);
		//	}
		//	return ST.status;
		//}
		//INLINE void FM_BUSY_SET(FM_ST *ST,int busyclock )
		//{
		//	TIME_TYPE expiry_period = MULTIPLY_TIME_BY_INT(ATTOTIME_IN_HZ(ST.clock), busyclock * ST.timer_prescaler);
		//	ST.busy_expiry_time = ADD_TIMES(FM_GET_TIME_NOW(ST.device.machine), expiry_period);
		//}
		//#else
		
		private byte FM_STATUS_FLAG(FM_ST ST) { return ST.status; }
		
		private void FM_BUSY_SET(FM_ST ST, int bclock) { }
		
		private void FM_BUSY_CLEAR(FM_ST ST) { }
		//#endif


		/* set algorithm connection */
		private static void setup_connection(FM_OPN OPN, FM_CH CH, int ch)
		{
			FM_OP carrier = OPN.out_fm[ch];

			FM_OP om1 = CH.connect1;
			FM_OP om2 = CH.connect3;
			FM_OP oc1 = CH.connect2;

			FM_OP memc = CH.mem_connect;

			switch (CH.ALGO)
			{
				case 0:
					/* M1---C1---MEM---M2---C2---OUT */
					om1 = OPN.c1;
					oc1 = OPN.mem;
					om2 = OPN.c2;
					memc = OPN.m2;
					break;
				case 1:
					/* M1------+-MEM---M2---C2---OUT */
					/*      C1-+                     */
					om1 = OPN.mem;
					oc1 = OPN.mem;
					om2 = OPN.c2;
					memc = OPN.m2;
					break;
				case 2:
					/* M1-----------------+-C2---OUT */
					/*      C1---MEM---M2-+          */
					om1 = OPN.c2;
					oc1 = OPN.mem;
					om2 = OPN.c2;
					memc = OPN.m2;
					break;
				case 3:
					/* M1---C1---MEM------+-C2---OUT */
					/*                 M2-+          */
					om1 = OPN.c1;
					oc1 = OPN.mem;
					om2 = OPN.c2;
					memc = OPN.c2;
					break;
				case 4:
					/* M1---C1-+-OUT */
					/* M2---C2-+     */
					/* MEM: not used */
					om1 = OPN.c1;
					oc1 = carrier;
					om2 = OPN.c2;
					memc = OPN.mem;  /* store it anywhere where it will not be used */
					break;
				case 5:
					/*    +----C1----+     */
					/* M1-+-MEM---M2-+-OUT */
					/*    +----C2----+     */
					om1 = null;   /* special mark */
					oc1 = carrier;
					om2 = carrier;
					memc = OPN.m2;
					break;
				case 6:
					/* M1---C1-+     */
					/*      M2-+-OUT */
					/*      C2-+     */
					/* MEM: not used */
					om1 = OPN.c1;
					oc1 = carrier;
					om2 = carrier;
					memc = OPN.mem;  /* store it anywhere where it will not be used */
					break;
				case 7:
					/* M1-+     */
					/* C1-+-OUT */
					/* M2-+     */
					/* C2-+     */
					/* MEM: not used*/
					om1 = carrier;
					oc1 = carrier;
					om2 = carrier;
					memc = OPN.mem;  /* store it anywhere where it will not be used */
					break;
			}

			CH.connect1 = om1;
			CH.connect3 = om2;
			CH.connect2 = oc1;
			CH.mem_connect = memc;

			CH.connect4 = carrier;
		}

		/* set detune & multiple */
		//INLINE
		
		private void set_det_mul(FM_ST ST, FM_CH CH, FM_SLOT SLOT, int v)
		{
			SLOT.mul = (v & 0x0f) != 0 ? (int)((v & 0x0f) * 2) : 1;
			SLOT.DT = ST.dt_tab[(v >> 4) & 7];
			CH.SLOT[SLOT1].Incr = -1;
		}

		/* set total level */
		//INLINE
		
		private void set_tl(FM_CH CH, FM_SLOT SLOT, int v)
		{
			SLOT.tl = (int)((v & 0x7f) << (ENV_BITS - 7)); /* 7bit TL */

			/* recalculate EG output */
			if ((SLOT.ssg & 0x08) != 0 && (SLOT.ssgn ^ (SLOT.ssg & 0x04)) != 0 && (SLOT.state > EG_REL))
				SLOT.vol_out = ((int)(0x200 - SLOT.volume) & MAX_ATT_INDEX) + SLOT.tl;
			else
				SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
		}

		/* set attack rate & key scale  */
		//INLINE
		
		private void set_ar_ksr(byte type, FM_CH CH, FM_SLOT SLOT, int v)
		{
			byte old_KSR = SLOT.KSR;

			SLOT.ar = (v & 0x1f) != 0 ? (int)(32 + ((v & 0x1f) << 1)) : 0;

			SLOT.KSR = (byte)(3 - (v >> 6));
			if (SLOT.KSR != old_KSR)
			{
				CH.SLOT[SLOT1].Incr = -1;
			}

			/* Even if it seems unnecessary, in some odd case, KSR and KC are both modified   */
			/* and could result in SLOT.kc remaining unchanged.                              */
			/* In such case, AR values would not be recalculated despite SLOT.ar has changed */
			/* This fixes the introduction music of Batman & Robin    (Eke-Eke)               */
			if ((SLOT.ar + SLOT.ksr) < 94 /*32+62*/)
			{
				SLOT.eg_sh_ar = eg_rate_shift[SLOT.ar + SLOT.ksr];
				SLOT.eg_sel_ar = eg_rate_select2612[SLOT.ar + SLOT.ksr];
			}
			else
			{
				SLOT.eg_sh_ar = 0;
				SLOT.eg_sel_ar = (byte) (18 * RATE_STEPS);  /* verified by Nemesis on real hardware */
			}
		}

		/* set decay rate */
		//INLINE
		
		private void set_dr(byte type, FM_SLOT SLOT, int v)
		{
			SLOT.d1r = (v & 0x1f) != 0 ? (int)(32 + ((v & 0x1f) << 1)) : 0;

			SLOT.eg_sh_d1r = eg_rate_shift[SLOT.d1r + SLOT.ksr];
			SLOT.eg_sel_d1r = eg_rate_select2612[SLOT.d1r + SLOT.ksr];
		}

		/* set sustain rate */
		//INLINE
		
		private void set_sr(byte type, FM_SLOT SLOT, int v)
		{
			SLOT.d2r = (v & 0x1f) != 0 ? (int)(32 + ((v & 0x1f) << 1)) : 0;

			SLOT.eg_sh_d2r = eg_rate_shift[SLOT.d2r + SLOT.ksr];
			SLOT.eg_sel_d2r = eg_rate_select2612[SLOT.d2r + SLOT.ksr];
		}

		/* set release rate */
		//INLINE
		
		private void set_sl_rr(byte type, FM_SLOT SLOT, int v)
		{
			SLOT.sl = sl_table[v >> 4];

			/* check EG state changes */
			if ((SLOT.state == EG_DEC) && (SLOT.volume >= (int)(SLOT.sl)))
				SLOT.state = EG_SUS;

			SLOT.rr = (int)(34 + ((v & 0x0f) << 2));

			SLOT.eg_sh_rr = eg_rate_shift[SLOT.rr + SLOT.ksr];
			SLOT.eg_sel_rr = eg_rate_select2612[SLOT.rr + SLOT.ksr];
		}

		/* advance LFO to next sample */
		//INLINE
		
		private void advance_lfo(FM_OPN OPN)
		{
			if (OPN.lfo_timer_overflow != 0)   /* LFO enabled ? */
			{
				/* increment LFO timer */
				OPN.lfo_timer += OPN.lfo_timer_add;

				/* when LFO is enabled, one level will last for 108, 77, 71, 67, 62, 44, 8 or 5 samples */
				while (OPN.lfo_timer >= OPN.lfo_timer_overflow)
				{
					OPN.lfo_timer -= OPN.lfo_timer_overflow;

					/* There are 128 LFO steps */
					OPN.lfo_cnt = (byte)((OPN.lfo_cnt + 1) & 127);

					// Valley Bell: Replaced old code (non-inverted triangle) with
					// the one from Genesis Plus GX 1.71.
					/* triangle (inverted) */
					/* AM: from 126 to 0 step -2, 0 to 126 step +2 */
					if (OPN.lfo_cnt < 64)
						OPN.LFO_AM = (int)((OPN.lfo_cnt ^ 63) << 1);
					else
						OPN.LFO_AM = (int)((OPN.lfo_cnt & 63) << 1);

					/* PM works with 4 times slower clock */
					OPN.LFO_PM = (int)(OPN.lfo_cnt >> 2);
				}
			}
		}

		//INLINE
		
		private void advance_eg_channel(FM_OPN OPN, FM_SLOT[] slot)
		{
			//unsigned int out;
			int i = 4; /* four operators per channel */
			int slotPtr = 0;

			do
			{
				FM_SLOT SLOT = slot[slotPtr];
				switch (SLOT.state)
				{
					case EG_ATT:    /* attack phase */
						if ((OPN.eg_cnt & ((1 << SLOT.eg_sh_ar) - 1)) == 0)
						{
							/* update attenuation level */
							SLOT.volume += (~SLOT.volume * (eg_inc[SLOT.eg_sel_ar + ((OPN.eg_cnt >> SLOT.eg_sh_ar) & 7)])) >> 4;

							/* check phase transition*/
							if (SLOT.volume <= MIN_ATT_INDEX)
							{
								SLOT.volume = MIN_ATT_INDEX;
								SLOT.state = (byte)((SLOT.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC); /* special case where SL=0 */
							}

							/* recalculate EG output */
							if ((SLOT.ssg & 0x08) != 0 && (SLOT.ssgn ^ (SLOT.ssg & 0x04)) != 0)  /* SSG-EG Output Inversion */
								SLOT.vol_out = ((int)(0x200 - SLOT.volume) & MAX_ATT_INDEX) + SLOT.tl;
							else
								SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
						}
						//Log.WriteLine(LogLevel.TRACE, String.format("SLOT.state:{0} SLOT.vol_out:{1}", SLOT.state, SLOT.vol_out));
						break;

					case EG_DEC:  /* decay phase */
						if ((OPN.eg_cnt & ((1 << SLOT.eg_sh_d1r) - 1)) == 0)
						{
							/* SSG EG type */
							if ((SLOT.ssg & 0x08) != 0)
							{
								/* update attenuation level */
								if (SLOT.volume < 0x200)
								{
									SLOT.volume += 4 * eg_inc[SLOT.eg_sel_d1r + ((OPN.eg_cnt >> SLOT.eg_sh_d1r) & 7)];

									/* recalculate EG output */
									if ((SLOT.ssgn ^ (byte)(SLOT.ssg & 0x04)) != 0)   /* SSG-EG Output Inversion */
										SLOT.vol_out = ((int)(0x200 - SLOT.volume) & MAX_ATT_INDEX) + SLOT.tl;
									else
										SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
								}

							}
							else
							{
								/* update attenuation level */
								SLOT.volume += eg_inc[SLOT.eg_sel_d1r + ((OPN.eg_cnt >> SLOT.eg_sh_d1r) & 7)];

								/* recalculate EG output */
								SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
							}

							/* check phase transition*/
							if (SLOT.volume >= (int)(SLOT.sl))
								SLOT.state = EG_SUS;
						}
						//Log.WriteLine(LogLevel.TRACE, String.format("SLOT.state:{0} SLOT.vol_out:{1}", SLOT.state, SLOT.vol_out));
						break;

					case EG_SUS:  /* sustain phase */
						if ((OPN.eg_cnt & ((1 << SLOT.eg_sh_d2r) - 1)) == 0)
						{
							/* SSG EG type */
							if ((SLOT.ssg & 0x08) != 0)
							{
								/* update attenuation level */
								if (SLOT.volume < 0x200)
								{
									SLOT.volume += 4 * eg_inc[SLOT.eg_sel_d2r + ((OPN.eg_cnt >> SLOT.eg_sh_d2r) & 7)];

									/* recalculate EG output */
									if ((SLOT.ssgn ^ (byte)(SLOT.ssg & 0x04)) != 0)   /* SSG-EG Output Inversion */
										SLOT.vol_out = ((int)(0x200 - SLOT.volume) & MAX_ATT_INDEX) + SLOT.tl;
									else
										SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
								}
							}
							else
							{
								/* update attenuation level */
								SLOT.volume += eg_inc[SLOT.eg_sel_d2r + ((OPN.eg_cnt >> SLOT.eg_sh_d2r) & 7)];

								/* check phase transition*/
								if (SLOT.volume >= MAX_ATT_INDEX)
									SLOT.volume = MAX_ATT_INDEX;
								/* do not change SLOT.state (verified on real chip) */

								/* recalculate EG output */
								SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
							}
						}
						//Log.WriteLine(LogLevel.TRACE, String.format("SLOT.state:{0} SLOT.vol_out:{1}", SLOT.state, SLOT.vol_out));
						break;

					case EG_REL:  /* release phase */
						if ((OPN.eg_cnt & ((1 << SLOT.eg_sh_rr) - 1)) == 0)
						{
							/* SSG EG type */
							if ((SLOT.ssg & 0x08) != 0)
							{
								/* update attenuation level */
								if (SLOT.volume < 0x200)
									SLOT.volume += 4 * eg_inc[SLOT.eg_sel_rr + ((OPN.eg_cnt >> SLOT.eg_sh_rr) & 7)];
								/* check phase transition */
								if (SLOT.volume >= 0x200)
								{
									SLOT.volume = MAX_ATT_INDEX;
									SLOT.state = EG_OFF;
								}
							}
							else
							{
								/* update attenuation level */
								SLOT.volume += eg_inc[SLOT.eg_sel_rr + ((OPN.eg_cnt >> SLOT.eg_sh_rr) & 7)];

								/* check phase transition*/
								if (SLOT.volume >= MAX_ATT_INDEX)
								{
									SLOT.volume = MAX_ATT_INDEX;
									SLOT.state = EG_OFF;
								}
							}

							/* recalculate EG output */
							SLOT.vol_out = (int)SLOT.volume + SLOT.tl;

						}
						//Log.WriteLine(LogLevel.TRACE, String.format("SLOT.state:{0} SLOT.vol_out:{1}", SLOT.state, SLOT.vol_out));
						break;
				}

				// Valley Bell: These few lines are missing in Genesis Plus GX' ym2612 core file.
				//              Disabling them fixes the SSG-EG.
				// Additional Note: Asterix and the Great Rescue: Level 1 sounds "better" with these lines,
				//					but less accurate.
				/*out = (((int)32)SLOT.volume);

				// negate output (changes come from alternate bit, init comes from attack bit)
				if ((SLOT.ssg&0x08) && (SLOT.ssgn&2) && (SLOT.state > EG_REL))
					out ^= MAX_ATT_INDEX;

				// we need to store the result here because we are going to change ssgn
				//  in next instruction
				SLOT.vol_out = out + SLOT.tl;*/






				slotPtr++;
				i--;
			} while (i != 0);

		}

		/* SSG-EG update process */
		/* The behavior is based upon Nemesis tests on real hardware */
		/* This is actually executed before each samples */
		//INLINE
		
		private void update_ssg_eg_channel(FM_SLOT[] slot)
		{
			int i = 4; /* four operators per channel */
			int slotPtr = 0;
			do
			{
				FM_SLOT SLOT = slot[slotPtr];

				/* detect SSG-EG transition */
				/* this is not required during release phase as the attenuation has been forced to MAX and output invert flag is not used */
				/* if an Attack Phase is programmed, inversion can occur on each sample */
				if ((SLOT.ssg & 0x08) != 0 && (SLOT.volume >= 0x200) && (SLOT.state > EG_REL))
				{
					if ((SLOT.ssg & 0x01) != 0)  /* bit 0 = hold SSG-EG */
					{
						/* set inversion flag */
						if ((SLOT.ssg & 0x02) != 0)
							SLOT.ssgn = 4;

						/* force attenuation level during decay phases */
						if ((SLOT.state != EG_ATT) && (SLOT.ssgn ^ (SLOT.ssg & 0x04)) == 0)
							SLOT.volume = MAX_ATT_INDEX;
					}
					else  /* loop SSG-EG */
					{
						/* toggle output inversion flag or reset Phase Generator */
						if ((SLOT.ssg & 0x02) != 0)
							SLOT.ssgn ^= 4;
						else
							SLOT.phase = 0;

						/* same as Key ON */
						if (SLOT.state != EG_ATT)
						{
							if ((SLOT.ar + SLOT.ksr) < 94 /*32+62*/)
							{
								SLOT.state = (byte)((SLOT.volume <= MIN_ATT_INDEX) ? ((SLOT.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC) : EG_ATT);
							}
							else
							{
								/* Attack Rate is maximal: directly switch to Decay or Substain */
								SLOT.volume = MIN_ATT_INDEX;
								SLOT.state = (byte)((SLOT.sl == MIN_ATT_INDEX) ? EG_SUS : EG_DEC);
							}
						}
					}

					/* recalculate EG output */
					if ((SLOT.ssgn ^ (SLOT.ssg & 0x04)) != 0)
						SLOT.vol_out = ((int)(0x200 - SLOT.volume) & MAX_ATT_INDEX) + SLOT.tl;
					else
						SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
				}

				/* next slot */
				slotPtr++;
				i--;
			} while (i != 0);
		}


		//INLINE
		
		private void update_phase_lfo_slot(FM_OPN OPN, FM_SLOT SLOT, int pms, int block_fnum)
		{
			int fnum_lfo = ((block_fnum & 0x7f0) >> 4) * 32 * 8;
			int lfo_fn_table_index_offset = lfo_pm_table[fnum_lfo + pms + OPN.LFO_PM];

			if (lfo_fn_table_index_offset != 0)    /* LFO phase modulation active */
			{
				byte blk = (byte)((block_fnum & 0x7000) >> 12);
				int fn = block_fnum & 0xfff;

				/* recalculate keyscale code */
				/*int kc = (blk<<2) | opn_fktable[fn >> 7];*/
				/* This really stupid bug caused a read outside of the
				   array [size 0x10] and returned invalid values.
				   This caused an annoying vibrato for some notes.
				   (Note: seems to be a copy-and-paste from OPNWriteReg . case 0xA0)
					Why are MAME cores always SOO buggy ?! */
				/* Oh, and before I forget: it's correct in fm.c */
				int kc = (blk << 2) | opn_fktable[fn >> 8];
				/* Thanks to Blargg - his patch that helped me to find this bug */

				/* recalculate (frequency) phase increment counter */
				int fc = (int)((OPN.fn_table[fn] >> (7 - blk)) + SLOT.DT[kc]);

				/* (frequency) phase overflow (credits to Nemesis) */
				if (fc < 0) fc += (int)OPN.fn_max;

				/* update phase */
				SLOT.phase += (int)((fc * SLOT.mul) >> 1);
			}
			else    /* LFO phase modulation  = zero */
			{
				SLOT.phase += (int)SLOT.Incr;
			}
		}

		//INLINE
		
		private void update_phase_lfo_channel(FM_OPN OPN, FM_CH CH)
		{
			int block_fnum = CH.block_fnum;

			int fnum_lfo = ((block_fnum & 0x7f0) >> 4) * 32 * 8;
			int lfo_fn_table_index_offset = lfo_pm_table[fnum_lfo + CH.pms + OPN.LFO_PM];

			block_fnum = (int)(block_fnum * 2 + lfo_fn_table_index_offset);

			if (lfo_fn_table_index_offset != 0)    /* LFO phase modulation active */
			{
				byte blk = (byte)((block_fnum & 0x7000) >> 12);
				int fn = block_fnum & 0xfff;

				/* recalculate keyscale code */
				/*int kc = (blk<<2) | opn_fktable[fn >> 7];*/
				/* the same stupid bug as above */
				int kc = (blk << 2) | opn_fktable[fn >> 8];

				/* recalculate (frequency) phase increment counter */
				int fc = (int)(OPN.fn_table[fn] >> (7 - blk));

				/* (frequency) phase overflow (credits to Nemesis) */
				int finc = fc + CH.SLOT[SLOT1].DT[kc];
				if (finc < 0) finc += (int)OPN.fn_max;
				CH.SLOT[SLOT1].phase += (int)(finc * CH.SLOT[SLOT1].mul) >> 1;

				finc = fc + CH.SLOT[SLOT2].DT[kc];
				if (finc < 0) finc += (int)OPN.fn_max;
				CH.SLOT[SLOT2].phase += (int)(finc * CH.SLOT[SLOT2].mul) >> 1;

				finc = fc + CH.SLOT[SLOT3].DT[kc];
				if (finc < 0) finc += (int)OPN.fn_max;
				CH.SLOT[SLOT3].phase += (int)(finc * CH.SLOT[SLOT3].mul) >> 1;

				finc = fc + CH.SLOT[SLOT4].DT[kc];
				if (finc < 0) finc += (int)OPN.fn_max;
				CH.SLOT[SLOT4].phase += (int)(finc * CH.SLOT[SLOT4].mul) >> 1;
			}
			else    /* LFO phase modulation  = zero */
			{
				CH.SLOT[SLOT1].phase += (int)CH.SLOT[SLOT1].Incr;
				CH.SLOT[SLOT2].phase += (int)CH.SLOT[SLOT2].Incr;
				CH.SLOT[SLOT3].phase += (int)CH.SLOT[SLOT3].Incr;
				CH.SLOT[SLOT4].phase += (int)CH.SLOT[SLOT4].Incr;
			}
		}

		/* update phase increment and envelope generator */
		//INLINE
		
		private void refresh_fc_eg_slot(FM_OPN OPN, FM_SLOT SLOT, int fc, int kc)
		{
			int ksr = kc >> SLOT.KSR;

			fc += SLOT.DT[kc];

			/* detects frequency overflow (credits to Nemesis) */
			if (fc < 0) fc += (int)OPN.fn_max;

			/* (frequency) phase increment counter */
			SLOT.Incr = (int)((fc * SLOT.mul) >> 1);

			if (SLOT.ksr != ksr)
			{
				SLOT.ksr = (byte)ksr;

				/* calculate envelope generator rates */
				if ((SLOT.ar + SLOT.ksr) < 32 + 62)
				{
					SLOT.eg_sh_ar = eg_rate_shift[SLOT.ar + SLOT.ksr];
					SLOT.eg_sel_ar = eg_rate_select2612[SLOT.ar + SLOT.ksr];
				}
				else
				{
					SLOT.eg_sh_ar = 0;
					SLOT.eg_sel_ar = (byte) (18 * RATE_STEPS); /* verified by Nemesis on real hardware (Attack phase is blocked) */
				}

				SLOT.eg_sh_d1r = eg_rate_shift[SLOT.d1r + SLOT.ksr];
				SLOT.eg_sh_d2r = eg_rate_shift[SLOT.d2r + SLOT.ksr];
				SLOT.eg_sh_rr = eg_rate_shift[SLOT.rr + SLOT.ksr];

				SLOT.eg_sel_d1r = eg_rate_select2612[SLOT.d1r + SLOT.ksr];
				SLOT.eg_sel_d2r = eg_rate_select2612[SLOT.d2r + SLOT.ksr];
				SLOT.eg_sel_rr = eg_rate_select2612[SLOT.rr + SLOT.ksr];
			}
		}

		/* update phase increment counters */
		//INLINE
		
		private void refresh_fc_eg_chan(FM_OPN OPN, FM_CH CH)
		{
			if (CH.SLOT[SLOT1].Incr == -1)
			{
				int fc = (int)CH.fc;
				int kc = CH.kcode;
				refresh_fc_eg_slot(OPN, CH.SLOT[SLOT1], fc, kc);
				refresh_fc_eg_slot(OPN, CH.SLOT[SLOT2], fc, kc);
				refresh_fc_eg_slot(OPN, CH.SLOT[SLOT3], fc, kc);
				refresh_fc_eg_slot(OPN, CH.SLOT[SLOT4], fc, kc);
			}
		}

		
		private int volume_calc(FM_SLOT OP, int AM) { return (OP.vol_out + (AM & OP.AMmask)); }

		//INLINE
		
		private int op_calc(int phase, int env, FM_OP pm)
		{
			int p;

			p = (int)((env << 3) + sin_tab[( ( (phase & ~FREQ_MASK) + ((int)pm.val << 15) ) >> FREQ_SH) & SIN_MASK]);

			if (p >= TL_TAB_LEN)
				return 0;
			return tl_tab[p];
		}

		//INLINE
		
		private int op_calc1(int phase, int env, int pm)
		{
			int p;

			p = (int)((env << 3) + sin_tab[( ( (phase & ~FREQ_MASK) + pm ) >> FREQ_SH) & SIN_MASK]);

			if (p >= TL_TAB_LEN)
				return 0;
			return tl_tab[p];
		}

		//INLINE
		
		private void chan_calc(YM2612 F2612, FM_OPN OPN, FM_CH CH)
		{
			int AM = OPN.LFO_AM >> CH.ams;
			int eg_out;

			if (CH.Muted != 0)
				return;

			OPN.m2.val = OPN.c1.val = OPN.c2.val = OPN.mem.val = 0;

			CH.mem_connect.val = CH.mem_value;  /* restore delayed sample (MEM) value to m2 or c2 */

			eg_out = volume_calc(CH.SLOT[SLOT1], AM);
			{
				int _out = CH.op1_out[0] + CH.op1_out[1];
				CH.op1_out[0] = CH.op1_out[1];

				if (CH.connect1 == null)
				{
					/* algorithm 5  */
					OPN.mem.val = OPN.c1.val = OPN.c2.val = CH.op1_out[0];
				}
				else
				{
					/* other algorithms */
					CH.connect1.val += CH.op1_out[0];
				}


				CH.op1_out[1] = 0;
				if (eg_out < ENV_QUIET)  /* SLOT 1 */
				{
					if (CH.FB == 0)
						_out = 0;

					CH.op1_out[1] = op_calc1(CH.SLOT[SLOT1].phase, eg_out, (_out << CH.FB));
					//Log.WriteLine(LogLevel.TRACE, String.format("SLOT1:eg_out:{0}", eg_out));
					//Log.WriteLine(LogLevel.TRACE, String.format("op1_out[1]:{0}", CH.op1_out[1]));
					//Log.WriteLine(LogLevel.TRACE, String.format("CH.SLOT[SLOT4].phase:{0}", CH.SLOT[SLOT1].phase));
					//Log.WriteLine(LogLevel.TRACE, String.format("out:{0}", _out));
					//Log.WriteLine(LogLevel.TRACE, String.format("CH.FB:{0}", CH.FB));
					//Log.WriteLine(LogLevel.TRACE, String.format("o<c:{0}", (_out << CH.FB)));
				}
			}

			eg_out = volume_calc(CH.SLOT[SLOT3], AM);
			if (eg_out < ENV_QUIET)
			{    /* SLOT 3 */
				CH.connect3.val += op_calc(CH.SLOT[SLOT3].phase, eg_out, OPN.m2);
				//Log.WriteLine(LogLevel.TRACE, String.format("SLOT3:eg_out:{0}", eg_out));
				//Log.WriteLine(LogLevel.TRACE, String.format("CH.connect3.val:{0}", CH.connect3.val));
			}

			eg_out = volume_calc(CH.SLOT[SLOT2], AM);
			if (eg_out < ENV_QUIET)
			{    /* SLOT 2 */
				CH.connect2.val += op_calc(CH.SLOT[SLOT2].phase, eg_out, OPN.c1);
				//Log.WriteLine(LogLevel.TRACE, String.format("SLOT2:eg_out:{0}", eg_out));
				//Log.WriteLine(LogLevel.TRACE, String.format("CH.connect2.val:{0}", CH.connect2.val));
			}

			eg_out = volume_calc(CH.SLOT[SLOT4], AM);
			if (eg_out < ENV_QUIET)
			{    /* SLOT 4 */
				CH.connect4.val += op_calc(CH.SLOT[SLOT4].phase, eg_out, OPN.c2);
				//Log.WriteLine(LogLevel.TRACE, String.format("SLOT4:eg_out:{0}", eg_out));
				//Log.WriteLine(LogLevel.TRACE, String.format("CH.connect4.val:{0}", CH.connect4.val));
				//Log.WriteLine(LogLevel.TRACE, String.format("CH.SLOT[SLOT4].phase:{0}", CH.SLOT[SLOT4].phase));
				//Log.WriteLine(LogLevel.TRACE, String.format("OPN.c2:{0}", OPN.c2.val));
			}

			/* store current MEM */
			CH.mem_value = OPN.mem.val;

			/* update phase counters AFTER output calculations */
			if (CH.pms != 0)
			{
				/* add support for 3 slot mode */
				if ((OPN.ST.mode & 0xC0) != 0 && (CH == F2612.CH[2]))
				{
					update_phase_lfo_slot(OPN, CH.SLOT[SLOT1], CH.pms, OPN.SL3.block_fnum[1]);
					update_phase_lfo_slot(OPN, CH.SLOT[SLOT2], CH.pms, OPN.SL3.block_fnum[2]);
					update_phase_lfo_slot(OPN, CH.SLOT[SLOT3], CH.pms, OPN.SL3.block_fnum[0]);
					update_phase_lfo_slot(OPN, CH.SLOT[SLOT4], CH.pms, CH.block_fnum);
				}
				else update_phase_lfo_channel(OPN, CH);
			}
			else  /* no LFO phase modulation */
			{
				CH.SLOT[SLOT1].phase += (int)CH.SLOT[SLOT1].Incr;
				CH.SLOT[SLOT2].phase += (int)CH.SLOT[SLOT2].Incr;
				CH.SLOT[SLOT3].phase += (int)CH.SLOT[SLOT3].Incr;
				CH.SLOT[SLOT4].phase += (int)CH.SLOT[SLOT4].Incr;
			}
		}

		private void FMCloseTable()
		{
			//# ifdef SAVE_SAMPLE
			//fclose(sample[0]);
			//#endif
			return;
		}


		/* CSM Key Controll */
		//INLINE
		
		private void CSMKeyControll(FM_OPN OPN, FM_CH CH)
		{
			/* all key ON (verified by Nemesis on real hardware) */
			FM_KEYON_CSM(OPN, CH, SLOT1);
			FM_KEYON_CSM(OPN, CH, SLOT2);
			FM_KEYON_CSM(OPN, CH, SLOT3);
			FM_KEYON_CSM(OPN, CH, SLOT4);
			OPN.SL3.key_csm = 1;
		}

		//# ifdef __STATE_H__
		/* FM channel save , internal state only */
		private void FMsave_state_channel(running_device device, FM_CH[] CH, int num_ch)
		{
			int slot, ch;
			int CHPtr = 0;

			for (ch = 0; ch < num_ch; ch++, CHPtr++)
			{
				/* channel */
				state_save_register_device_item_array(device, ch, CH[CHPtr].op1_out);
				state_save_register_device_item(device, ch, CH[CHPtr].fc);
				/* slots */
				for (slot = 0; slot < 4; slot++)
				{
					FM_SLOT SLOT = CH[CHPtr].SLOT[slot];
					state_save_register_device_item(device, ch * 4 + slot, SLOT.phase);
					state_save_register_device_item(device, ch * 4 + slot, SLOT.state);
					state_save_register_device_item(device, ch * 4 + slot, SLOT.volume);
				}
			}
		}


        private void FMsave_state_st(running_device device, FM_ST ST)
		{
// #if FM_BUSY_FLAG_SUPPORT
//	state_save_register_device_item(device, 0, ST.busy_expiry_time.seconds );
//	state_save_register_device_item(device, 0, ST.busy_expiry_time.attoseconds );
// #endif
			state_save_register_device_item(device, 0, ST.address);
			state_save_register_device_item(device, 0, ST.irq);
			state_save_register_device_item(device, 0, ST.irqmask);
			state_save_register_device_item(device, 0, ST.status);
			state_save_register_device_item(device, 0, ST.mode);
			state_save_register_device_item(device, 0, ST.prescaler_sel);
			state_save_register_device_item(device, 0, ST.fn_h);
			state_save_register_device_item(device, 0, ST.TA);
			state_save_register_device_item(device, 0, ST.TAC);
			state_save_register_device_item(device, 0, ST.TB);
			state_save_register_device_item(device, 0, ST.TBC);
		}
		//#endif /* _STATE_H */

		//#if BUILD_OPN
		/* write a OPN mode register 0x20-0x2f */
		private void OPNWriteMode(FM_OPN OPN, int r, int v)
		{
			byte c;
			FM_CH CH;

			switch (r)
			{
				case 0x21:  /* Test */
					break;
				case 0x22:  /* LFO FREQ (YM2608/YM2610/YM2610B/YM2612) */
					if ((v & 8) != 0) /* LFO enabled ? */
					{
						/*if (!OPN.lfo_timer_overflow)
						{
							// restart LFO
							OPN.lfo_cnt   = 0;
							OPN.lfo_timer = 0;
							OPN.LFO_AM    = 0;
							OPN.LFO_PM    = 0;
						}*/

						OPN.lfo_timer_overflow = lfo_samples_per_step[v & 7] << LFO_SH;
					}
					else
					{
						// Valley Bell: Ported from Genesis Plus GX 1.71
						// hold LFO waveform in reset state
						OPN.lfo_timer_overflow = 0;
						OPN.lfo_timer = 0;
						OPN.lfo_cnt = 0;


						OPN.LFO_PM = 0;
						OPN.LFO_AM = 126;
						//OPN.lfo_timer_overflow = 0;
					}
					break;
				case 0x24:  /* timer A High 8*/
					OPN.ST.TA = (OPN.ST.TA & 0x03) | (((int)v) << 2);
					break;
				case 0x25:  /* timer A Low 2*/
					OPN.ST.TA = (OPN.ST.TA & 0x3fc) | (v & 3);
					break;
				case 0x26:  /* timer B */
					OPN.ST.TB = (byte)v;
					break;
				case 0x27:  /* mode, timer control */
					set_timers(OPN, OPN.ST, OPN.ST.param, v);
					break;
				case 0x28:  /* key on / off */
					c = (byte)(v & 0x03);
					if (c == 3) break;
					if ((v & 0x04) != 0 && (OPN.type & TYPE_6CH) != 0) c += 3;
					CH = OPN.P_CH[c];
					if ((v & 0x10) != 0) FM_KEYON(OPN, CH, SLOT1); else FM_KEYOFF(OPN, CH, SLOT1);
					if ((v & 0x20) != 0) FM_KEYON(OPN, CH, SLOT2); else FM_KEYOFF(OPN, CH, SLOT2);
					if ((v & 0x40) != 0) FM_KEYON(OPN, CH, SLOT3); else FM_KEYOFF(OPN, CH, SLOT3);
					if ((v & 0x80) != 0) FM_KEYON(OPN, CH, SLOT4); else FM_KEYOFF(OPN, CH, SLOT4);
					break;
			}
		}

		/* write a OPN register (0x30-0xff) */
		private void OPNWriteReg(FM_OPN OPN, int r, int v)
		{
			FM_CH CH;
			FM_SLOT SLOT;

			byte c = OPN_CHAN(r);

			if (c == 3) return; /* 0xX3,0xX7,0xXB,0xXF */

			if (r >= 0x100) c += 3;

			CH = OPN.P_CH[c];

			SLOT = CH.SLOT[OPN_SLOT(r)];

			switch (r & 0xf0)
			{
				case 0x30:  /* DET , MUL */
					set_det_mul(OPN.ST, CH, SLOT, v);
					break;

				case 0x40:  /* TL */
					set_tl(CH, SLOT, v);
					break;

				case 0x50:  /* KS, AR */
					set_ar_ksr(OPN.type, CH, SLOT, v);
					break;

				case 0x60:  /* bit7 = AM ENABLE, DR */
					set_dr(OPN.type, SLOT, v);

					if ((OPN.type & TYPE_LFOPAN)!=0) /* YM2608/2610/2610B/2612 */
					{
						SLOT.AMmask = (v & 0x80)!=0 ? 0xffff_ffff : 0;
					}
					break;

				case 0x70:  /*     SR */
					set_sr(OPN.type, SLOT, v);
					break;

				case 0x80:  /* SL, RR */
					set_sl_rr(OPN.type, SLOT, v);
					break;

				case 0x90:  /* SSG-EG */
					SLOT.ssg = (byte)(v & 0x0f);

					/* recalculate EG output */
					if (SLOT.state > EG_REL)
					{
						if ((SLOT.ssg & 0x08)!=0 && (SLOT.ssgn ^ (SLOT.ssg & 0x04))!=0)
							SLOT.vol_out = ((int)(0x200 - SLOT.volume) & MAX_ATT_INDEX) + SLOT.tl;
						else
							SLOT.vol_out = (int)SLOT.volume + SLOT.tl;
					}

					/* SSG-EG envelope shapes :

					E AtAlH
					1 0 0 0  \\\\

					1 0 0 1  \___

					1 0 1 0  \/\/
							  ___
					1 0 1 1  \

					1 1 0 0  ////
							  ___
					1 1 0 1  /

					1 1 1 0  /\/\

					1 1 1 1  /___


					E = SSG-EG enable


					The shapes are generated using Attack, Decay and Sustain phases.

					Each single character in the diagrams above represents this whole
					sequence:

					- when KEY-ON = 1, normal Attack phase is generated (*without* any
					  difference when compared to normal mode),

					- later, when envelope level reaches minimum level (max volume),
					  the EG switches to Decay phase (which works with bigger steps
					  when compared to normal mode - see below),

					- later when envelope level passes the SL level,
					  the EG swithes to Sustain phase (which works with bigger steps
					  when compared to normal mode - see below),

					- finally when envelope level reaches maximum level (min volume),
					  the EG switches to Attack phase again (depends on actual waveform).

					Important is that when switch to Attack phase occurs, the phase counter
					of that operator will be zeroed-out (as in normal KEY-ON) but not always.
					(I havent found the rule for that - perhaps only when the output level is low)

					The difference (when compared to normal Envelope Generator mode) is
					that the resolution in Decay and Sustain phases is 4 times lower;
					this results in only 256 steps instead of normal 1024.
					In other words:
					when SSG-EG is disabled, the step inside of the EG is one,
					when SSG-EG is enabled, the step is four (in Decay and Sustain phases).

					Times between the level changes are the same in both modes.


					Important:
					Decay 1 Level (so called SL) is compared to actual SSG-EG output, so
					it is the same in both SSG and no-SSG modes, with this exception:

					when the SSG-EG is enabled and is generating raising levels
					(when the EG output is inverted) the SL will be found at wrong level !!!
					For example, when SL=02:
						0 -6 = -6dB in non-inverted EG output
						96-6 = -90dB in inverted EG output
					Which means that EG compares its level to SL as usual, and that the
					output is simply inverted afterall.


					The Yamaha's manuals say that AR should be set to 0x1f (max speed).
					That is not necessary, but then EG will be generating Attack phase.

					*/


					break;

				case 0xa0:
					switch (OPN_SLOT(r))
					{
						case 0:     /* 0xa0-0xa2 : FNUM1 */
							if (IsVGMInit != 0)
								OPN.ST.fn_h = (byte)(CH.block_fnum >> 8);
							{
								int fn = (int)((((int)((OPN.ST.fn_h) & 7)) << 8) + v);
								byte blk = (byte)(OPN.ST.fn_h >> 3);
								/* keyscale code */
								CH.kcode = (byte)((blk << 2) | opn_fktable[fn >> 7]);
								/* phase increment counter */
								CH.fc = OPN.fn_table[fn * 2] >> (7 - blk);

								/* store fnum in clear form for LFO PM calculations */
								CH.block_fnum = (int)((blk << 11) | fn);

								CH.SLOT[SLOT1].Incr = -1;
							}
							break;
						case 1:     /* 0xa4-0xa6 : FNUM2,BLK */
							OPN.ST.fn_h = (byte)(v & 0x3f);
							if (IsVGMInit != 0)  // workaround for stupid Kega Fusion init block
								CH.block_fnum = (int)((int)(OPN.ST.fn_h << 8) | (CH.block_fnum & 0xFF));
							break;
						case 2:     /* 0xa8-0xaa : 3CH FNUM1 */
							if (IsVGMInit != 0)
								OPN.SL3.fn_h = (byte)(OPN.SL3.block_fnum[c] >> 8);
							if (r < 0x100)
							{
								int fn = (int)((((int)(OPN.SL3.fn_h & 7)) << 8) + v);
								byte blk = (byte)(OPN.SL3.fn_h >> 3);
								/* keyscale code */
								OPN.SL3.kcode[c] = (byte)((blk << 2) | opn_fktable[fn >> 7]);
								/* phase increment counter */
								OPN.SL3.fc[c] = OPN.fn_table[fn * 2] >> (7 - blk);
								OPN.SL3.block_fnum[c] = (int)((int)(blk << 11) | fn);
								(OPN.P_CH)[2].SLOT[SLOT1].Incr = -1;
							}
							break;
						case 3:     /* 0xac-0xae : 3CH FNUM2,BLK */
							if (r < 0x100)
							{
								OPN.SL3.fn_h = (byte)(v & 0x3f);
								if (IsVGMInit != 0)
									OPN.SL3.block_fnum[c] = (int)((int)(OPN.SL3.fn_h << 8) | (OPN.SL3.block_fnum[c] & 0xFF));
							}
							break;
					}
					break;

				case 0xb0:
					switch (OPN_SLOT(r))
					{
						case 0:     /* 0xb0-0xb2 : FB,ALGO */
							{
								int feedback = (v >> 3) & 7;
								CH.ALGO = (byte)(v & 7);
								CH.FB = (byte)(feedback != 0 ? (byte)(feedback + 6) : 0);
								setup_connection(OPN, CH, c);
							}
							break;
						case 1:     /* 0xb4-0xb6 : L , R , AMS , PMS (YM2612/YM2610B/YM2610/YM2608) */
							if ((OPN.type & TYPE_LFOPAN) != 0)
							{
								/* b0-2 PMS */
								CH.pms = (v & 7) * 32; /* CH.pms = PM depth * 32 (index in lfo_pm_table) */

								/* b4-5 AMS */
								CH.ams = lfo_ams_depth_shift[(v >> 4) & 0x03];

								/* PAN :  b7 = L, b6 = R */
								OPN.pan[c * 2] = (v & 0x80) != 0 ? 0xffff_ffff : 0;
								OPN.pan[c * 2 + 1] = (v & 0x40) != 0 ? 0xffff_ffff : 0;

							}
							break;
					}
					break;
			}
		}

		/* initialize time tables */
		private void init_timetables(FM_OPN OPN, double freqbase)
		{
			int i, d;
			double rate;

			/* DeTune table */
			for (d = 0; d <= 3; d++)
			{
				for (i = 0; i <= 31; i++)
				{
					rate = ((double)dt_tab[d * 32 + i]) * freqbase * (1 << (FREQ_SH - 10)); /* -10 because chip works with 10.10 fixed point, while we use 16.16 */
					OPN.ST.dt_tab[d][i] = (int)rate;
					OPN.ST.dt_tab[d + 4][i] = -OPN.ST.dt_tab[d][i];
				}
			}

			/* there are 2048 FNUMs that can be generated using FNUM/BLK registers
			but LFO works with one more bit of a precision so we really need 4096 elements */
			/* calculate fnumber . increment counter table */
			for (i = 0; i < 4096; i++)
			{
				/* freq table for octave 7 */
				/* OPN phase increment counter = 20bit */
				/* the correct formula is : F-Number = (144 * fnote * 2^20 / M) / 2^(B-1) */
				/* where sample clock is  M/144 */
				/* this means the increment value for one clock sample is FNUM * 2^(B-1) = FNUM * 64 for octave 7 */
				/* we also need to handle the ratio between the chip frequency and the emulated frequency (can be 1.0)  */
				OPN.fn_table[i] = (int)((double)i * 32 * freqbase * (1 << (FREQ_SH - 10))); /* -10 because chip works with 10.10 fixed point, while we use 16.16 */
			}

			/* maximal frequency is required for Phase overflow calculation, register size is 17 bits (Nemesis) */
			OPN.fn_max = (int)((double)0x20000 * freqbase * (1 << (FREQ_SH - 10)));
		}

		/* prescaler set (and make time tables) */
		private void OPNSetPres(FM_OPN OPN, int pres, int timer_prescaler, int SSGpres)
		{
			/* frequency base */
			OPN.ST.freqbase = (OPN.ST.rate != 0) ? ((double)OPN.ST.clock / OPN.ST.rate) / pres : 0;

			/* EG is updated every 3 samples */
			OPN.eg_timer_add = (int)((1 << EG_SH) * OPN.ST.freqbase);
			OPN.eg_timer_overflow = (3) * (1 << EG_SH);

			/* LFO timer increment (every samples) */
			OPN.lfo_timer_add = (int)((1 << LFO_SH) * OPN.ST.freqbase);

			/* Timer base time */
			OPN.ST.timer_prescaler = timer_prescaler;

			/* SSG part  prescaler set */
			if (SSGpres != 0) OPN.ST.SSG.set_clock.accept(OPN.ST.param, (int)(OPN.ST.clock * 2 / SSGpres));

			/* make time tables */
			init_timetables(OPN, OPN.ST.freqbase);
		}

		private void reset_channels(FM_ST ST, FM_CH[] CH, int num)
		{
			int c, s;

			for (c = 0; c < num; c++)
			{
				//memset(&CH[c], 0x00, sizeof(FM_CH));
				CH[c].mem_value = 0;
				CH[c].op1_out[0] = 0;
				CH[c].op1_out[1] = 0;
				CH[c].fc = 0;
				for (s = 0; s < 4; s++)
				{
					//memset(&CH[c].SLOT[s], 0x00, sizeof(FM_SLOT));
					CH[c].SLOT[s].Incr = -1;
					CH[c].SLOT[s].key = 0;
					CH[c].SLOT[s].phase = 0;
					CH[c].SLOT[s].ssg = 0;
					CH[c].SLOT[s].ssgn = 0;
					CH[c].SLOT[s].state = EG_OFF;
					CH[c].SLOT[s].volume = MAX_ATT_INDEX;
					CH[c].SLOT[s].vol_out = MAX_ATT_INDEX;
				}
			}
		}

		/* initialize generic tables */
		private void init_tables()
		{
			short i, x;
			short n;
			double o, m;

			/* build Linear Power Table */
			for (x = 0; x < TL_RES_LEN; x++)
			{
				m = (1 << 16) / Math.pow(2, (x + 1) * (ENV_STEP / 4.0) / 8.0);
				m = Math.floor(m);

				/* we never reach (1<<16) here due to the (x+1) */
				/* result fits within 16 bits at maximum */

				//n = (short)m;     /* 16 bits here */
				//n >>= 4;        /* 12 bits here */
				//if ((n & 1) != 0)      /* round to nearest */
				//	n = (short)((n >> 1) + 1);
				//else
				//	n = (short)(n >> 1);
				///* 11 bits here (rounded) */
				//n <<= 2;        /* 13 bits here (as in real chip) */
				//tl_tab[x * 2 + 0] = n;
				//tl_tab[x * 2 + 1] = (short)-tl_tab[x * 2 + 0];

				int ni = (int)m & 0xffff;
				ni >>= 4;
				if ((ni & 1) != 0)      /* round to nearest */
					ni = ((ni >> 1) + 1);
				else
					ni = (ni >> 1);
				ni <<= 2;

				/* 14 bits (with sign bit) */
				tl_tab[x * 2 + 0] = ni;// (short)ni;
				tl_tab[x * 2 + 1] = -ni;// (short)-tl_tab[x * 2 + 0];

				/* one entry in the 'Power' table use the following format, xxxxxyyyyyyyys with:            */
				/*        s = sign bit                                                                      */
				/* yyyyyyyy = 8-bits decimal part (0-TL_RES_LEN)                                            */
				/* xxxxx    = 5-bits integer 'shift' value (0-31) but, since Power table output is 13 bits, */
				/*            any value above 13 (included) would be discarded.                             */
				for (i = 1; i < 13; i++)
				{
					tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN] = (tl_tab[x * 2 + 0] >> i);
					tl_tab[x * 2 + 1 + i * 2 * TL_RES_LEN] = -tl_tab[x * 2 + 0 + i * 2 * TL_RES_LEN];
				}
			}

			//for (x = 0; x < TL_TAB_LEN; x++)
			//{
			//	Log.WriteLine(LogLevel.TRACE, String.format("{0},", tl_tab[x]));
			//}

			/* build Logarithmic Sinus table */
			for (i = 0; i < SIN_LEN; i++)
			{
				/* non-standard sinus */
				m = Math.sin(((i * 2) + 1) * Math.PI / SIN_LEN); /* checked against the real chip */
				/* we never reach zero here due to ((i*2)+1) */

				if (m > 0.0)
					o = 8 * Math.log(1.0 / m) / Math.log(2.0);    /* convert to 'decibels' */
				else
					o = 8 * Math.log(-1.0 / m) / Math.log(2.0);   /* convert to 'decibels' */

				o = o / (ENV_STEP / 4);

				n = (short)(2.0 * o);
				if ((n & 1) != 0)          /* round to nearest */
					n = (short)((n >> 1) + 1);
				else
					n = (short)(n >> 1);

				/* 13-bits (8.5) value is formatted for above 'Power' table */
				sin_tab[i] = (int)(n * 2 + (m >= 0.0 ? 0 : 1));
			}
            //for (x = 0; x < SIN_LEN; x++)
            //{
            //    Log.WriteLine(LogLevel.TRACE, String.format("{0},", sin_tab[x]));
            //}

            /* build LFO PM modulation table */
            for (i = 0; i < 8; i++) /* 8 PM depths */
			{
				byte fnum;
				for (fnum = 0; fnum < 128; fnum++) /* 7 bits meaningful of F-NUMBER */
				{
					byte value;
					byte step;
					int offset_depth = (int)i;
					int offset_fnum_bit;
					int bit_tmp;

					for (step = 0; step < 8; step++)
					{
						value = 0;
						for (bit_tmp = 0; bit_tmp < 7; bit_tmp++) /* 7 bits */
						{
							if ((fnum & (1 << (int)bit_tmp)) != 0) /* only if bit "bit_tmp" is set */
							{
								offset_fnum_bit = bit_tmp * 8;
								value += lfo_pm_output[offset_fnum_bit + offset_depth][step];
							}
						}
						/* 32 steps for LFO PM (sinus) */
						lfo_pm_table[(fnum * 32 * 8) + (i * 32) + step + 0] = value;
						lfo_pm_table[(fnum * 32 * 8) + (i * 32) + (step ^ 7) + 8] = value;
						lfo_pm_table[(fnum * 32 * 8) + (i * 32) + step + 16] = -value;
						lfo_pm_table[(fnum * 32 * 8) + (i * 32) + (step ^ 7) + 24] = -value;
					}

				}
			}

			//for (int j = 0; j < 128*8*32; j++)
			//{
            //	Log.WriteLine(LogLevel.TRACE, String.format("{0},", lfo_pm_table[j]));
			//}

			//# ifdef SAVE_SAMPLE
			//sample[0] = fopen("sampsum.pcm", "wb");
			//#endif
		}

		//#endif /* BUILD_OPN */

		//#if BUILD_YM2612 || BUILD_YM3438
		/*******************************************************************************/
		/*      YM2612 local section                                                   */
		/*******************************************************************************/

		FM_CH[] cch = new FM_CH[6];

		/* Generate samples for one of the YM2612s */
		public void ym2612_update_one(fm.FM_base chip, int[][] buffer, int length)
		{
			YM2612 F2612 = (YM2612)chip;
			FM_OPN OPN = F2612.OPN;
			FM_OP[] out_fm = OPN.out_fm;
			int i;
			int[] bufL, bufR;
			int dacout;
			//FM_CH[] cch = new FM_CH[6];
			int lt, rt;

			/* set bufer */
			bufL = buffer[0];
			bufR = buffer[1];

			cch[0] = F2612.CH[0];
			cch[1] = F2612.CH[1];
			cch[2] = F2612.CH[2];
			cch[3] = F2612.CH[3];
			cch[4] = F2612.CH[4];
			cch[5] = F2612.CH[5];

			if (F2612.MuteDAC == 0)
				dacout = F2612.dacout;
			else
				dacout = 0;

			/* refresh PG and EG */
			refresh_fc_eg_chan(OPN, cch[0]);
			refresh_fc_eg_chan(OPN, cch[1]);
			if ((OPN.ST.mode & 0xc0) != 0)
			{
				/* 3SLOT MODE */
				if (cch[2].SLOT[SLOT1].Incr == -1)
				{
					refresh_fc_eg_slot(OPN, cch[2].SLOT[SLOT1], (int)OPN.SL3.fc[1], OPN.SL3.kcode[1]);
					refresh_fc_eg_slot(OPN, cch[2].SLOT[SLOT2], (int)OPN.SL3.fc[2], OPN.SL3.kcode[2]);
					refresh_fc_eg_slot(OPN, cch[2].SLOT[SLOT3], (int)OPN.SL3.fc[0], OPN.SL3.kcode[0]);
					refresh_fc_eg_slot(OPN, cch[2].SLOT[SLOT4], (int)cch[2].fc, cch[2].kcode);
				}
			}
			else refresh_fc_eg_chan(OPN, cch[2]);
			refresh_fc_eg_chan(OPN, cch[3]);
			refresh_fc_eg_chan(OPN, cch[4]);
			refresh_fc_eg_chan(OPN, cch[5]);
			if (length == 0)
			{
				update_ssg_eg_channel(cch[0].SLOT);//[SLOT1]);
				update_ssg_eg_channel(cch[1].SLOT);//[SLOT1]);
				update_ssg_eg_channel(cch[2].SLOT);//[SLOT1]);
				update_ssg_eg_channel(cch[3].SLOT);//[SLOT1]);
				update_ssg_eg_channel(cch[4].SLOT);//[SLOT1]);
				update_ssg_eg_channel(cch[5].SLOT);//[SLOT1]);
			}


			/* buffering */
			for (i = 0; i < length; i++)
			{
				/* clear outputs */
				out_fm[0].val = 0;
				out_fm[1].val = 0;
				out_fm[2].val = 0;
				out_fm[3].val = 0;
				out_fm[4].val = 0;
				out_fm[5].val = 0;

				/* update SSG-EG output */
				update_ssg_eg_channel(cch[0].SLOT);//[SLOT1]);
				update_ssg_eg_channel(cch[1].SLOT);//[SLOT1]);
				update_ssg_eg_channel(cch[2].SLOT);//[SLOT1]);
				update_ssg_eg_channel(cch[3].SLOT);//[SLOT1]);
				update_ssg_eg_channel(cch[4].SLOT);//[SLOT1]);
				update_ssg_eg_channel(cch[5].SLOT);//[SLOT1]);

				/* calculate FM */
				if (F2612.dac_test == 0)
				{
					chan_calc(F2612, OPN, cch[0]);
					chan_calc(F2612, OPN, cch[1]);
					chan_calc(F2612, OPN, cch[2]);
					chan_calc(F2612, OPN, cch[3]);
					chan_calc(F2612, OPN, cch[4]);
					if (F2612.dacen != 0)
						cch[5].connect4.val += dacout;
					else
						chan_calc(F2612, OPN, cch[5]);
				}
				else
				{
					out_fm[0].val = out_fm[1].val = dacout;
					out_fm[2].val = out_fm[3].val = dacout;
					out_fm[5].val = dacout;
				}

				/* advance LFO */
				advance_lfo(OPN);
				
				
				//Log.WriteLine(LogLevel.TRACE, String.format("OPN.eg_timer_add:{0} OPN.eg_timer:{1} OPN.eg_timer_overflow:{2}", OPN.eg_timer_add,OPN.eg_timer,OPN.eg_timer_overflow));


				/* advance envelope generator */
				OPN.eg_timer += OPN.eg_timer_add;
				while (OPN.eg_timer >= OPN.eg_timer_overflow)
				{
					OPN.eg_timer -= OPN.eg_timer_overflow;
					OPN.eg_cnt++;

					advance_eg_channel(OPN, cch[0].SLOT);//[SLOT1]);
					advance_eg_channel(OPN, cch[1].SLOT);//[SLOT1]);
					advance_eg_channel(OPN, cch[2].SLOT);//[SLOT1]);
					advance_eg_channel(OPN, cch[3].SLOT);//[SLOT1]);
					advance_eg_channel(OPN, cch[4].SLOT);//[SLOT1]);
					advance_eg_channel(OPN, cch[5].SLOT);//[SLOT1]);
				}

				/*fprintf(hFile, "%u", FileSample, out_fm[0]);
				for (lt = 0; lt < 6; lt ++)
					fprintf(hFile, "\t%d", out_fm[lt]);
				fprintf(hFile, "\n");
				FileSample ++;*/

				if (out_fm[0].val > 8192) out_fm[0].val = 8192;
				else if (out_fm[0].val < -8192) out_fm[0].val = -8192;
				if (out_fm[1].val > 8192) out_fm[1].val = 8192;
				else if (out_fm[1].val < -8192) out_fm[1].val = -8192;
				if (out_fm[2].val > 8192) out_fm[2].val = 8192;
				else if (out_fm[2].val < -8192) out_fm[2].val = -8192;
				if (out_fm[3].val > 8192) out_fm[3].val = 8192;
				else if (out_fm[3].val < -8192) out_fm[3].val = -8192;
				if (out_fm[4].val > 8192) out_fm[4].val = 8192;
				else if (out_fm[4].val < -8192) out_fm[4].val = -8192;
				if (out_fm[5].val > 8192) out_fm[5].val = 8192;
				else if (out_fm[5].val < -8192) out_fm[5].val = -8192;

				int bitShift = 0;
				/* 6-channels mixing  */
				lt = (int)((int)(out_fm[0].val >> bitShift) & OPN.pan[0]);
				rt = (int)((int)(out_fm[0].val >> bitShift) & OPN.pan[1]);
				lt +=(int)((int)(out_fm[1].val >> bitShift) & OPN.pan[2]);
				rt +=(int)((int)(out_fm[1].val >> bitShift) & OPN.pan[3]);
				lt +=(int)((int)(out_fm[2].val >> bitShift) & OPN.pan[4]);
				rt +=(int)((int)(out_fm[2].val >> bitShift) & OPN.pan[5]);
				lt +=(int)((int)(out_fm[3].val >> bitShift) & OPN.pan[6]);
				rt +=(int)((int)(out_fm[3].val >> bitShift) & OPN.pan[7]);
				if (F2612.dac_test == 0)
				{
					lt += (int)((int)(out_fm[4].val >> bitShift) & OPN.pan[8]);
					rt += (int)((int)(out_fm[4].val >> bitShift) & OPN.pan[9]);
				}
				else
				{
					lt += (int)dacout;
					lt += (int)dacout;
				}
				lt += (int)((int)(out_fm[5].val >> bitShift) & OPN.pan[10]);
				rt += (int)((int)(out_fm[5].val >> bitShift) & OPN.pan[11]);

				//      Limit( lt, MAXOUT, MINOUT );
				//      Limit( rt, MAXOUT, MINOUT );

				//# ifdef SAVE_SAMPLE
				//SAVE_ALL_CHANNELS
				//#endif

				/* buffering */
				if ((F2612.WaveOutMode & 0x01) != 0)
					F2612.WaveL = lt;
				if ((F2612.WaveOutMode & 0x02) != 0)
					F2612.WaveR = rt;
				if ((F2612.WaveOutMode ^ 0x03) != 0)
					F2612.WaveOutMode ^= 0x03;
				bufL[i] = F2612.WaveL;
				bufR[i] = F2612.WaveR;

				//if (F2612.WaveL != 0)
					//Log.WriteLine(LogLevel.TRACE, String.format("L:{0} R:{1}", F2612.WaveL, F2612.WaveR));

				/* CSM mode: if CSM Key ON has occured, CSM Key OFF need to be sent       */
				/* only if Timer A does not overflow again (i.e CSM Key ON not set again) */
				OPN.SL3.key_csm <<= 1;

				/* timer A control */
				//INTERNAL_TIMER_A( &OPN.ST , cch[2] )
				{
					if (OPN.ST.TAC != 0 && (OPN.ST.timer_handler == null))
						if ((OPN.ST.TAC -= (int)(OPN.ST.freqbase * 4096)) <= 0)
						{
							TimerAOver(OPN.ST);
							// CSM mode total level latch and auto key on
							if ((OPN.ST.mode & 0x80) != 0)
								CSMKeyControll(OPN, cch[2]);
						}
				}

				/* CSM Mode Key ON still disabled */
				if ((OPN.SL3.key_csm & 2) != 0)
				{
					/* CSM Mode Key OFF (verified by Nemesis on real hardware) */
					FM_KEYOFF_CSM(cch[2], SLOT1);
					FM_KEYOFF_CSM(cch[2], SLOT2);
					FM_KEYOFF_CSM(cch[2], SLOT3);
					FM_KEYOFF_CSM(cch[2], SLOT4);
					OPN.SL3.key_csm = 0;
				}
			}

			/* timer B control */
			//	INTERNAL_TIMER_B(&OPN.ST,length)
		}

		//#ifdef __STATE_H__
		private void ym2612_postload(fm.FM_base chip)
		{
			if (chip != null)
			{
				YM2612 F2612 = (YM2612)chip;
				int r;

				/* DAC data & port */
				F2612.dacout = ((int)F2612.REGS[0x2a] - 0x80) << 6;   /* level unknown */
				F2612.dacen = (byte)(F2612.REGS[0x2d] & 0x80);
				/* OPN registers */
				/* DT / MULTI , TL , KS / AR , AMON / DR , SR , SL / RR , SSG-EG */
				for (r = 0x30; r < 0x9e; r++)
					if ((r & 3) != 3)
					{
						OPNWriteReg(F2612.OPN, r, F2612.REGS[r]);
						OPNWriteReg(F2612.OPN, r | 0x100, F2612.REGS[r | 0x100]);
					}
				/* FB / CONNECT , L / R / AMS / PMS */
				for (r = 0xb0; r < 0xb6; r++)
					if ((r & 3) != 3)
					{
						OPNWriteReg(F2612.OPN, r, F2612.REGS[r]);
						OPNWriteReg(F2612.OPN, r | 0x100, F2612.REGS[r | 0x100]);
					}
				/* channels */
				/*FM_channel_postload(F2612.CH,6);*/
			}
		}

		private void YM2612_save_state(YM2612 F2612, running_device device)
		{
			state_save_register_device_item_array(device, 0, F2612.REGS);
			FMsave_state_st(device, F2612.OPN.ST);
			FMsave_state_channel(device, F2612.CH, 6);
			/* 3slots */
			state_save_register_device_item_array(device, 0, F2612.OPN.SL3.fc);
			state_save_register_device_item(device, 0, F2612.OPN.SL3.fn_h);
			state_save_register_device_item_array(device, 0, F2612.OPN.SL3.kcode);
			/* address register1 */
			state_save_register_device_item(device, 0, F2612.addr_A1);
		}

        private void state_save_register_device_item_array(running_device device, int v, int[] fc)
        {
            throw new UnsupportedOperationException();
        }

        //#endif /* _STATE_H */

        /* initialize YM2612 emulator(s) */
        //void * ym2612_init(void *param, running_device *device, int clock, int rate,
        //               FM_TIMERHANDLER timer_handler,FM_IRQHANDLER IRQHandler)
        public YM2612 ym2612_init(YM2612 param, int clock, int rate,
					   FM_ST.dlgFM_TIMERHANDLER timer_handler, FM_ST.dlgFM_IRQHANDLER IRQHandler)
		{
			YM2612 F2612;

			/* allocate extend state space */
			//F2612 = auto_alloc_clear(device.machine, YM2612);
			F2612 = new YM2612();// (YM2612*)malloc(sizeof(YM2612));
			if (F2612 == null)
				return null;
			//memset(F2612, 0x00, sizeof(YM2612));
			/* allocate total level table (128kb space) */
			init_tables();

			F2612.OPN = new FM_OPN();
			F2612.OPN.ST.param = param;
			F2612.OPN.type = TYPE_YM2612;
			F2612.OPN.P_CH = F2612.CH;
			//F2612.OPN.ST.device = device;
			F2612.OPN.ST.clock = (int)clock;
			F2612.OPN.ST.rate = (int)rate;
			/* F2612.OPN.ST.irq = 0; */
			/* F2612.OPN.ST.status = 0; */
			/* Extend handler */
			F2612.OPN.ST.timer_handler = timer_handler;
			F2612.OPN.ST.IRQ_Handler = IRQHandler;

			if (PseudoSt != 0)
				F2612.WaveOutMode = 0x01;
			else
				F2612.WaveOutMode = 0x03;
			/*hFile = fopen("YM2612.log", "wt");
			fprintf(hFile, "Clock: %d, Sample Rate: %d\n", clock, rate);
			fprintf(hFile, "Sample\tCh 0\tCh 1\tCh 2\tCh 3\tCh 4\tCh 5\n");
			FileSample = 0;*/

			//# ifdef __STATE_H__
			//	YM2612_save_state(F2612, device);
			//#endif
			return F2612;
		}

		/* shut down emulator */
		private void ym2612_shutdown(fm.FM_base chip)
		{
			YM2612 F2612 = (YM2612)chip;
			//fclose(hFile);

			FMCloseTable();
			//auto_free(F2612.OPN.ST.device.machine, F2612);
			//free(F2612);
		}

		/* reset one of chip */
		public void ym2612_reset_chip(fm.FM_base chip)
		{
			int i;
			YM2612 F2612 = (YM2612)chip;
			FM_OPN OPN = F2612.OPN;

			OPNSetPres(OPN, 6 * 24, 6 * 24, 0);
			/* status clear */
			FM_IRQMASK_SET(OPN.ST, 0x03);
			FM_BUSY_CLEAR(OPN.ST);
			//OPNWriteMode(OPN,0x27,0x30); /* mode 0 , timer reset */

			OPN.eg_timer = 0;
			OPN.eg_cnt = 0;

			OPN.lfo_timer = 0;
			OPN.lfo_cnt = 0;
			OPN.LFO_AM = 126;
			OPN.LFO_PM = 0;

			OPN.ST.TAC = 0;
			OPN.ST.TBC = 0;

			OPN.SL3.key_csm = 0;

			OPN.ST.status = 0;
			OPN.ST.mode = 0;

			for (i = 0; i < 512; i++) F2612.REGS[i] = 0x00;
			//memset(F2612.REGS, 0x00, sizeof(byte) * 512);

			OPNWriteMode(OPN, 0x22, 0x00);

			OPNWriteMode(OPN, 0x27, 0x30);
			OPNWriteMode(OPN, 0x26, 0x00);
			OPNWriteMode(OPN, 0x25, 0x00);
			OPNWriteMode(OPN, 0x24, 0x00);

			reset_channels(OPN.ST, F2612.CH, 6);

			for (i = 0xb6; i >= 0xb4; i--)
			{
				OPNWriteReg(OPN, i, 0xc0);
				OPNWriteReg(OPN, i | 0x100, 0xc0);
			}
			for (i = 0xb2; i >= 0x30; i--)
			{
				OPNWriteReg(OPN, i, 0);
				OPNWriteReg(OPN, i | 0x100, 0);
			}

			/* DAC mode clear */
			F2612.dacen = 0;
			F2612.dac_test = 0;
			F2612.dacout = 0;

			if (F2612.WaveOutMode == 0x02)
				F2612.WaveOutMode >>= 1;
		}

		/* YM2612 write */
		/* n = number  */
		/* a = address */
		/* v = value   */
		public int ym2612_write(byte ChipID, fm.FM_base chip, int a, byte v)
		{
			//Log.WriteLine(LogLevel.TRACE, String.format("a:{0:x} v:{1:x}",a,v));
			


			YM2612 F2612 = (YM2612)chip;
			int addr;

			//F2612.OPN.eg_timer = 0;

			v &= 0xff;  /* adjust to 8 bit bus */

			switch (a & 3)
			{
				case 0: /* address port 0 */
					F2612.OPN.ST.address = v;
					F2612.addr_A1 = 0;
					break;

				case 1: /* data port 0    */
					if (F2612.addr_A1 != 0)
						break;  /* verified on real YM2608 */

					addr = F2612.OPN.ST.address;
					F2612.REGS[addr] = v;
					switch (addr & 0xf0)
					{
						case 0x20:  /* 0x20-0x2f Mode */
							switch (addr)
							{
								case 0x2a:  /* DAC data (YM2612) */
									ym2612_update_req(ChipID, F2612.OPN.ST.param);
									F2612.dacout = ((int)v - 0x80) << 6;   /* level unknown */
									break;
								case 0x2b:  /* DAC Sel  (YM2612) */
									/* b7 = dac enable */
									F2612.dacen = (byte)(v & 0x80);
									break;
								case 0x2C:  // undocumented: DAC Test Reg
											// b5 = volume enable
									F2612.dac_test = (byte)(v & 0x20);
									break;
								default:    /* OPN section */
									ym2612_update_req(ChipID, F2612.OPN.ST.param);
									/* write register */
									OPNWriteMode(F2612.OPN, addr, v);
									break;
							}
							break;
						default:    /* 0x30-0xff OPN section */
							//ym2612_update_req(F2612.OPN.ST.param);
							/* write register */
							OPNWriteReg(F2612.OPN, addr, v);
							break;
					}
					break;

				case 2: /* address port 1 */
					F2612.OPN.ST.address = v;
					F2612.addr_A1 = 1;
					break;

				case 3: /* data port 1    */
					if (F2612.addr_A1 != 1)
						break;  /* verified on real YM2608 */

					addr = F2612.OPN.ST.address;
					F2612.REGS[addr | 0x100] = v;
					ym2612_update_req(ChipID, F2612.OPN.ST.param);
					OPNWriteReg(F2612.OPN, addr | 0x100, v);
					break;
			}
			return F2612.OPN.ST.irq;
		}

		private byte ym2612_read(fm.FM_base chip, int a)
		{
			YM2612 F2612 = (YM2612)chip;

			switch (a & 3)
			{
				case 0: /* status 0 */
					return FM_STATUS_FLAG(F2612.OPN.ST);
				case 1:
				case 2:
				case 3:
					//LOG(LOG_WAR,("YM2612 // #%p:A=%d read unmapped area\n",F2612.OPN.ST.param,a));
					return FM_STATUS_FLAG(F2612.OPN.ST);
			}
			return 0;
		}

		private int ym2612_timer_over(byte ChipID, fm.FM_base chip, int c)
		{
			YM2612 F2612 = (YM2612)chip;

			if (c != 0)
			{   /* Timer B */
				TimerBOver(F2612.OPN.ST);
			}
			else
			{   /* Timer A */
				ym2612_update_req(ChipID, F2612.OPN.ST.param);
				/* timer update */
				TimerAOver(F2612.OPN.ST);
				/* CSM mode key,TL controll */
				if ((F2612.OPN.ST.mode & 0xc0) == 0x80)
				{   /* CSM mode total level latch and auto key on */
					CSMKeyControll(F2612.OPN, F2612.CH[2]);
				}
			}
			return F2612.OPN.ST.irq;
		}


		public void ym2612_set_mutemask(byte ChipID, fm.FM_base chip, int MuteMask)
		{
			YM2612 F2612 = (YM2612)chip;
			byte CurChn;

			for (CurChn = 0; CurChn < 6; CurChn++)
				F2612.CH[CurChn].Muted = (byte)((MuteMask >> CurChn) & 0x01);
			F2612.MuteDAC = (byte)((MuteMask >> 6) & 0x01);

			return;
		}

		private void ym2612_setoptions(byte Flags)
		{
			PseudoSt = (byte)((Flags >> 2) & 0x01);

			return;
		}
		//#endif /* (BUILD_YM2612||BUILD_YM3238) */




		public fm2612()
        {
			initSl_table();
			initEg_rate_select();
			initEg_rate_shift();
		}

		public fm.callBack_update_request ym2612_update_request;
		private void ym2612_update_req(byte ChipID, YM2612 chip) { ym2612_update_request.accept(ChipID, chip); }

		private void state_save_register_device_item(running_device device, int v, byte fn_h)
		{
			throw new UnsupportedOperationException();
		}

		private void state_save_register_device_item_array(running_device device, int v, byte[] rEGS)
		{
			throw new UnsupportedOperationException();
		}

		private void state_save_register_device_item(running_device device, int v, int volume)
		{
			throw new UnsupportedOperationException();
		}

//		private void state_save_register_device_item(running_device device, int ch, int fc)
//		{
//			throw new UnsupportedOperationException();
//		}

//		private void state_save_register_device_item_array(running_device device, int ch, int[] op1_out)
//		{
//			throw new UnsupportedOperationException();
//		}


		public class FM_OP
        {
			public int val = 0;
        }
	}

	class running_device
    {
    }
