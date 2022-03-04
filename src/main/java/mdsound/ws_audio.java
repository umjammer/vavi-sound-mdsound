package mdsound;
import java.util.Arrays;

 public class ws_audio extends Instrument
 {
  private static final int DefaultWSwanClockValue = 3072000;
  private wsa_state[] chip = new wsa_state[] { new wsa_state(DefaultWSwanClockValue), new wsa_state(DefaultWSwanClockValue) };
  private int sampleRate = 44100;
  private int masterClock = DefaultWSwanClockValue;
  private double sampleCounter = 0;
  private int[][] frm = new int[][] { new int[1], new int[1] };
  private int[][] before = new int[][] { new int[1], new int[1] };

  @Override public String getName() { return "WonderSwan"; }
  @Override public String getShortName() { return "WSwan"; }

  @Override public void Reset(byte ChipID)
  {
   ws_audio_reset(chip[ChipID]);
  }

  @Override public int Start(byte ChipID, int clock)
  {
   return Start(ChipID, clock, DefaultWSwanClockValue, null);
  }

  @Override public int Start(byte ChipID, int clock, int ClockValue, Object... option)
  {
   chip[ChipID] = ws_audio_init(clock, ClockValue);
   sampleRate = clock;
   masterClock = ClockValue;

   visVolume = new int[2][][];
   visVolume[0] = new int[2][];
   visVolume[1] = new int[2][];
   visVolume[0][0] = new int[2];
   visVolume[1][0] = new int[2];
   visVolume[0][1] = new int[2];
   visVolume[1][1] = new int[2];

   return clock;
  }

  @Override public void Stop(byte ChipID)
  {
   ws_audio_done(chip[ChipID]);
  }

  @Override public void Update(byte ChipID, int[][] outputs, int samples)
  {
   for (int i = 0; i < samples; i++)
   {
    outputs[0][i] = 0;
    outputs[1][i] = 0;

    sampleCounter += (masterClock / 128.0) / sampleRate;
    int upc = (int)sampleCounter;
    while (sampleCounter >= 1)
    {
     ws_audio_update(chip[ChipID], (int)1, frm);

     outputs[0][i] += frm[0][0];
     outputs[1][i] += frm[1][0];

     sampleCounter -= 1.0;
    }

    if (upc != 0)
    {
     outputs[0][i] /= upc;
     outputs[1][i] /= upc;
     before[0][i] = outputs[0][i];
     before[1][i] = outputs[1][i];
    }
    else
    {
     outputs[0][i] = before[0][i];
     outputs[1][i] = before[1][i];
    }

    outputs[0][i] <<= 2;
    outputs[1][i] <<= 2;
   }

   visVolume[ChipID][0][0] = outputs[0][0];
   visVolume[ChipID][0][1] = outputs[1][0];
  }

  @Override public int Write(byte ChipID, int port, int adr, int data)
  {
   ws_audio_port_write(chip[ChipID], (byte)(adr + 0x80), (byte)data);
   return 0;
  }

  public int WriteMem(byte ChipID, int adr, int data)
  {
   ws_write_ram_byte(chip[ChipID], (int)adr, (byte)data);
   return 0;
  }

  public void SetMute(byte chipID, int v)
  {
  }






  //ws_initialio.h

  ////////////////////////////////////////////////////////////////////////////////
  // Initial I/O values
  ////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////

  private byte[] initialIoValue = new byte[] {
 0x00,//0
 0x00,//1
 (byte)0x9d,//2
 (byte)0xbb,//3
 0x00,//4
 0x00,//5
 0x00,//6
 0x26,//7
 (byte)0xfe,//8
 (byte)0xde,//9
 (byte)0xf9,//a
 (byte)0xfb,//b
 (byte)0xdb,//c
 (byte)0xd7,//d
 0x7f,//e
 (byte)0xf5,//f
 0x00,//10
 0x00,//11
 0x00,//12
 0x00,//13
 0x01,//14
 0x00,//15
 (byte)0x9e,//16
 (byte)0x9b,//17
 0x00,//18
 0x00,//19
 0x00,//1a
 0x00,//1b
 (byte)0x99,//1c
 (byte)0xfd,//1d
 (byte)0xb7,//1e
 (byte)0xdf,//1f
 0x30,//20
 0x57,//21
 0x75,//22
 0x76,//23
 0x15,//24
 0x73,//25
 0x77,//26
 0x77,//27
 0x20,//28
 0x75,//29
 0x50,//2a
 0x36,//2b
 0x70,//2c
 0x67,//2d
 0x50,//2e
 0x77,//2f
 0x57,//30
 0x54,//31
 0x75,//32
 0x77,//33
 0x75,//34
 0x17,//35
 0x37,//36
 0x73,//37
 0x50,//38
 0x57,//39
 0x60,//3a
 0x77,//3b
 0x70,//3c
 0x77,//3d
 0x10,//3e
 0x73,//3f
 0x00,//40
 0x00,//41
 0x00,//42
 0x00,//43
 0x00,//44
 0x00,//45
 0x00,//46
 0x00,//47
 0x00,//48
 0x00,//49
 0x00,//4a
 0x00,//4b
 0x00,//4c
 0x00,//4d
 0x00,//4e
 0x00,//4f
 0x00,//50
 0x00,//51
 0x00,//52
 0x00,//53
 0x00,//54
 0x00,//55
 0x00,//56
 0x00,//57
 0x00,//58
 0x00,//59
 0x00,//5a
 0x00,//5b
 0x00,//5c
 0x00,//5d
 0x00,//5e
 0x00,//5f
 0x0a,//60
 0x00,//61
 0x00,//62
 0x00,//63
 0x00,//64
 0x00,//65
 0x00,//66
 0x00,//67
 0x00,//68
 0x00,//69
 0x00,//6a
 0x0f,//6b
 0x00,//6c
 0x00,//6d
 0x00,//6e
 0x00,//6f
 0x00,//70
 0x00,//71
 0x00,//72
 0x00,//73
 0x00,//74
 0x00,//75
 0x00,//76
 0x00,//77
 0x00,//78
 0x00,//79
 0x00,//7a
 0x00,//7b
 0x00,//7c
 0x00,//7d
 0x00,//7e
 0x00,//7f
 (byte)0xFF,//80
 0x07,//81
 (byte)0xFF,//82
 0x07,//83
 (byte)0xFF,//84
 0x07,//85
 (byte)0xFF,//86
 0x07,//87
 0x00,//88
 0x00,//89
 0x00,//8a
 0x00,//8b
 0x00,//8c
 0x1f,//8d 1d ?
 0x00,//8e
 0x00,//8f
 0x00,//90
 0x00,//91
 0x00,//92
 0x00,//93
 0x00,//94
 0x00,//95
 0x00,//96
 0x00,//97
 0x00,//98
 0x00,//99
 0x00,//9a
 0x00,//9b
 0x00,//9c
 0x00,//9d
 0x03,//9e
 0x00,//9f
 (byte)0x87-2,//a0
 0x00,//a1
 0x00,//a2
 0x00,//a3
 0x0,//a4 2b
 0x0,//a5 7f
 0x4f,//a6
 (byte)0xff,//a7 cf ?
 0x00,//a8
 0x00,//a9
 0x00,//aa
 0x00,//ab
 0x00,//ac
 0x00,//ad
 0x00,//ae
 0x00,//af
 0x00,//b0
 (byte)0xdb,//b1
 0x00,//b2
 0x00,//b3
 0x00,//b4
 0x40,//b5
 0x00,//b6
 0x00,//b7
 0x00,//b8
 0x00,//b9
 0x01,//ba
 0x00,//bb
 0x42,//bc
 0x00,//bd
 (byte)0x83,//be
 0x00,//bf
 0x2f,//c0
 0x3f,//c1
 (byte)0xff,//c2
 (byte)0xff,//c3
 0x00,//c4
 0x00,//c5
 0x00,//c6
 0x00,//c7

 (byte)0xd1,//c8?
 (byte)0xd1,//c9
 (byte)0xd1,//ca
 (byte)0xd1,//cb
 (byte)0xd1,//cc
 (byte)0xd1,//cd
 (byte)0xd1,//ce
 (byte)0xd1,//cf
 (byte)0xd1,//d0
 (byte)0xd1,//d1
 (byte)0xd1,//d2
 (byte)0xd1,//d3
 (byte)0xd1,//d4
 (byte)0xd1,//d5
 (byte)0xd1,//d6
 (byte)0xd1,//d7
 (byte)0xd1,//d8
 (byte)0xd1,//d9
 (byte)0xd1,//da
 (byte)0xd1,//db
 (byte)0xd1,//dc
 (byte)0xd1,//dd
 (byte)0xd1,//de
 (byte)0xd1,//df
 (byte)0xd1,//e0
 (byte)0xd1,//e1
 (byte)0xd1,//e2
 (byte)0xd1,//e3
 (byte)0xd1,//e4
 (byte)0xd1,//e5
 (byte)0xd1,//e6
 (byte)0xd1,//e7
 (byte)0xd1,//e8
 (byte)0xd1,//e9
 (byte)0xd1,//ea
 (byte)0xd1,//eb
 (byte)0xd1,//ec
 (byte)0xd1,//ed
 (byte)0xd1,//ee
 (byte)0xd1,//ef
 (byte)0xd1,//f0
 (byte)0xd1,//f1
 (byte)0xd1,//f2
 (byte)0xd1,//f3
 (byte)0xd1,//f4
 (byte)0xd1,//f5
 (byte)0xd1,//f6
 (byte)0xd1,//f7
 (byte)0xd1,//f8
 (byte)0xd1,//f9
 (byte)0xd1,//fa
 (byte)0xd1,//fb
 (byte)0xd1,//fc
 (byte)0xd1,//fd
 (byte)0xd1,//fe
 (byte)0xd1 //ff
  };






  //ws_audio.c

  //# include <stdlib.h>
  //# include <string.h> // for memset
  //# include <stddef.h> // for NULL

  //# include "../../stdtype.h"
  //# include "../EmuStructs.h"
  //# include "../EmuCores.h"
  //# include "../snddef.h"
  //# include "../EmuHelper.h"
  //# include "../RatioCntr.h"
  //# include "ws_audio.h"


  //  typedef struct _wsa_state wsa_state;

  //static (int)8 ws_audio_init(final DEV_GEN_CFG* cfg, DEV_INFO* retDevInf);
  //static void ws_audio_reset(void* info);
  //  static void ws_audio_done(void* info);
  //  static void ws_audio_update(void* info, (int)32 length, DEV_SMPL** buffer);
  //  static void ws_audio_port_write(void* info, (int)8 port, (int)8 value);
  //  static (int)8 ws_audio_port_read(void* info, (int)8 port);
  //  static void ws_audio_process(wsa_state* chip);
  //  static void ws_audio_sounddma(wsa_state* chip);
  //  static void ws_write_ram_byte(void* info, int offset, (int)8 value);
  //  static (int)8 ws_read_ram_byte(void* info, int offset);
  //  static void ws_set_mute_mask(void* info, (int)32 MuteMask);
  //  static (int)32 ws_get_mute_mask(void* info);


  //  static DEVDEF_RWFUNC devFunc[] =
  //  {
  // {RWF_REGISTER | RWF_WRITE, DEVRW_A8D8, 0, ws_audio_port_write},
  // {RWF_REGISTER | RWF_READ, DEVRW_A8D8, 0, ws_audio_port_read},
  // {RWF_MEMORY | RWF_WRITE, DEVRW_A16D8, 0, ws_write_ram_byte},
  // {RWF_MEMORY | RWF_READ, DEVRW_A16D8, 0, ws_read_ram_byte},
  // //{RWF_MEMORY | RWF_WRITE, DEVRW_BLOCK, 0, ws_write_ram_block},
  // {RWF_CHN_MUTE | RWF_WRITE, DEVRW_ALL, 0, ws_set_mute_mask},
  // {0x00, 0x00, 0, NULL}
  //};
  //  static DEV_DEF devDef =
  //  {
  // "WonderSwan", "in_wsr", 0x00000000,

  // ws_audio_init,
  // ws_audio_done,
  // ws_audio_reset,
  // ws_audio_update,

  // NULL, // SetOptionBits
  // ws_set_mute_mask,
  // NULL, // SetPanning
  // NULL, // SetSampleRateChangeCallback
  // NULL, // LinkDevice

  // devFunc, // rwFuncs
  //};

  //  final DEV_DEF* devDefList_WSwan[] =
  //  {
  // &devDef,
  // NULL
  //};


  //  typedef (int)8   byte;
  //#include "ws_initialIo.h"

  private enum wsIORam
  {
   SNDP(0x80) //#define SNDP chip.ws_ioRam[0x80]
   , SNDV(0x88) //#define SNDV chip.ws_ioRam[0x88]
   , SNDSWP(0x8C) //#define SNDSWP chip.ws_ioRam[0x8C]
   , SWPSTP(0x8D) //#define SWPSTP chip.ws_ioRam[0x8D]
   , NSCTL(0x8E) //#define NSCTL chip.ws_ioRam[0x8E]
   , WAVDTP(0x8F) //#define WAVDTP chip.ws_ioRam[0x8F]
   , SNDMOD(0x90) //#define SNDMOD chip.ws_ioRam[0x90]
   , SNDOUT(0x91) //#define SNDOUT chip.ws_ioRam[0x91]
   , PCSRL(0x92) //#define PCSRL chip.ws_ioRam[0x92]
   , PCSRH(0x93) //#define PCSRH chip.ws_ioRam[0x93]
   , DMASL(0x40) //#define DMASL chip.ws_ioRam[0x40]
   , DMASH(0x41) //#define DMASH chip.ws_ioRam[0x41]
   , DMASB(0x42) //#define DMASB chip.ws_ioRam[0x42]
   , DMADB(0x43) //#define DMADB chip.ws_ioRam[0x43]
   , DMADL(0x44) //#define DMADL chip.ws_ioRam[0x44]
   , DMADH(0x45) //#define DMADH chip.ws_ioRam[0x45]
   , DMACL(0x46) //#define DMACL chip.ws_ioRam[0x46]
   , DMACH(0x47) //#define DMACH chip.ws_ioRam[0x47]
   , DMACTL(0x48) //#define DMACTL chip.ws_ioRam[0x48]
   , SDMASL(0x4A) //#define SDMASL chip.ws_ioRam[0x4A]
   , SDMASH(0x4B) //#define SDMASH chip.ws_ioRam[0x4B]
   , SDMASB(0x4C) //#define SDMASB chip.ws_ioRam[0x4C]
   , SDMACL(0x4E) //#define SDMACL chip.ws_ioRam[0x4E]
   , SDMACH(0x4F) //#define SDMACH chip.ws_ioRam[0x4F]
   , SDMACTL(0x52); //#define SDMACTL chip.ws_ioRam[0x52]
            int v;
            wsIORam(int v) { this.v = v; }
            static wsIORam valueOf(int v) { return Arrays.stream(values()).filter(e -> e.v == v).findFirst().get(); }
  }

  ////SoundDMA の転送間隔
  //// 実際の数値が分からないので、予想です
  //// サンプリング周期から考えてみて以下のようにした
  //// 12KHz = 1.00HBlank = 256cycles間隔
  //// 16KHz = 0.75HBlank = 192cycles間隔
  //// 20KHz = 0.60HBlank = 154cycles間隔
  //// 24KHz = 0.50HBlank = 128cycles間隔
  private static int[] DMACycles = new int[] { 256, 192, 154, 128 };

  private class WS_AUDIO
  {
   public int wave;
   public byte lvol;
   public byte rvol;
   public int offset;
   public int delta;
   public byte pos;
   public byte Muted;
  }
  // WS_AUDIO;

  private class RATIO_CNTR
  {
   public long inc;    // counter increment
   public long val;    // current value
  }
  //RATIO_CNTR;
  private void RC_SET_RATIO(RATIO_CNTR rc, int mul, int div)
  {
   rc.inc = (long)((((long)mul << 20) + div / 2) / div);//RC_SHIFT=20
  }
  private void RC_STEP(RATIO_CNTR rc)
  {
   rc.val += rc.inc;
  }

  private void RC_RESET(RATIO_CNTR rc)
  {
   rc.val = 0;
  }

  private void RC_RESET_PRESTEP(RATIO_CNTR rc)
  {
   rc.val = ((long)1 << 20) - rc.inc;
  }

  private int RC_GET_VAL(RATIO_CNTR rc)
  {
   return (int)(rc.val >> 20);
  }

  private void RC_MASK(RATIO_CNTR rc)
  {
   rc.val &= (((long)1 << 20) - 1);
  }

  private class wsa_state
  {
   //  DEV_DATA _devData;

   public WS_AUDIO[] ws_audio = new WS_AUDIO[] { new WS_AUDIO(), new WS_AUDIO(), new WS_AUDIO(), new WS_AUDIO() };
   public RATIO_CNTR HBlankTmr = new RATIO_CNTR();
   public short SweepTime;
   public byte SweepStep;
   public short SweepCount;
   public int SweepFreq;
   public byte NoiseType;
   public int NoiseRng;
   public int MainVolume;
   public byte PCMVolumeLeft;
   public byte PCMVolumeRight;

   public byte[] ws_ioRam = new byte[0x100];
   public byte[] ws_internalRam;

   public int clock = DEFAULT_CLOCK;
   public int smplrate = DEFAULT_CLOCK / 128;
   public float ratemul;

   public wsa_state(int masterClock)
            {
    clock = masterClock;
    smplrate = clock / 128;
            }
  };

  private static final int DEFAULT_CLOCK = 3072000;


  private wsa_state ws_audio_init(int sampleRate,int masterClock)//DEV_GEN_CFG cfg, DEV_INFO retDevInf)
  {
   wsa_state chip;

   chip = new wsa_state(masterClock);// (wsa_state)calloc(1, sizeof(wsa_state));

   // actual size is 64 KB, but the audio chip can only access 16 KB
   chip.ws_internalRam = new byte[0x4000];// ((int)8*)malloc(0x4000);

   //chip.clock = cfg.clock;
   //// According to http://daifukkat.su/docs/wsman/, the headphone DAC update is (clock / 128)
   //// and sound channels update during every master clock cycle. (clock / 1)
   //chip.smplrate = cfg.clock / 128;

   ////SRATE_CUSTOM_HIGHEST(cfg.srMode, chip.smplrate, cfg.smplRate);
   //if (cfg.srMode == 0x01//DEVRI_SRMODE_CUSTOM
   // || (cfg.srMode == 0x01//DEVRI_SRMODE_HIGHEST
   //         && chip.smplrate < cfg.smplRate)
   // ) chip.smplrate = cfg.smplRate;

   chip.ratemul = (float)chip.clock * 65536.0f / (float)chip.smplrate;
   // one step every 256 cycles
   RC_SET_RATIO(chip.HBlankTmr, chip.clock, chip.smplrate * 256);

   ws_set_mute_mask(chip, 0x00);

   //chip._devData.chipInf = chip;
   //INIT_DEVINF(retDevInf, chip._devData, chip.smplrate, devDef);

   return chip;
  }

  private void ws_audio_reset(wsa_state info)
  {
   wsa_state chip = (wsa_state)info;
   int muteMask;
   int i;

   muteMask = ws_get_mute_mask(chip);
   chip.ws_audio = new WS_AUDIO[] { new WS_AUDIO(), new WS_AUDIO(), new WS_AUDIO(), new WS_AUDIO() };// memset(&chip.ws_audio, 0, sizeof(WS_AUDIO));
   ws_set_mute_mask(chip, muteMask);

   chip.SweepTime = 0;
   chip.SweepStep = 0;
   chip.NoiseType = 0;
   chip.NoiseRng = 1;
   chip.MainVolume = 0x02;    // 0x04
   chip.PCMVolumeLeft = 0;
   chip.PCMVolumeRight = 0;

   RC_RESET(chip.HBlankTmr);

   for (i = 0x80; i < 0xc9; i++)
    ws_audio_port_write(chip, (byte)i, initialIoValue[i]);
  }

  private void ws_audio_done(wsa_state info)
  {
   wsa_state chip = (wsa_state)info;

   //free(chip.ws_internalRam);
   //free(chip);

   return;
  }

  //OSWANの擬似乱数の処理と同等のつもり
  //#define BIT(n) (1<<n)
  private int[] noise_mask = new int[]
  {
        0b11,//BIT(0)|BIT(1),
                                0b110011,//BIT(0)|BIT(1)|BIT(4)|BIT(5),
                                0b11011,//BIT(0)|BIT(1)|BIT(3)|BIT(4),
                                0b1010011,//BIT(0)|BIT(1)|BIT(4)|BIT(6),
                                0b101,//BIT(0)|BIT(2),
                                0b1001,//BIT(0)|BIT(3),
                                0b10001,//BIT(0)|BIT(4),
                                0b11101//BIT(0)|BIT(2)|BIT(3)|BIT(4)
  };

  private int[] noise_bit = new int[]
  {
        0b1000_0000_0000_0000,//BIT(15),
                                0b0100_0000_0000_0000,//BIT(14),
                                0b0010_0000_0000_0000,//BIT(13),
                                0b0001_0000_0000_0000,//BIT(12),
                                0b0000_1000_0000_0000,//BIT(11),
                                0b0000_0100_0000_0000,//BIT(10),
                                0b0000_0010_0000_0000,//BIT(9),
                                0b0000_0001_0000_0000//BIT(8)
  };

  private void ws_audio_update(wsa_state info, int length, int[][] buffer)
  {
   wsa_state chip = (wsa_state)info;
   int[] bufL;
   int[] bufR;
   int i;
   byte ch, cnt;
   short w;    // could fit into INT8
   int l, r;

   bufL = buffer[0];
   bufR = buffer[1];
   for (i = 0; i < length; i++)
   {
    int swpCount;

    RC_STEP(chip.HBlankTmr);
    for (swpCount = RC_GET_VAL(chip.HBlankTmr); swpCount > 0; swpCount--)
     ws_audio_process(chip);
    RC_MASK(chip.HBlankTmr);

    l = r = 0;

    for (ch = 0; ch < 4; ch++)
    {
     if (chip.ws_audio[ch].Muted != 0)
      continue;

     if ((ch == 1) && ((chip.ws_ioRam[(int)wsIORam.SNDMOD.v] & 0x20) != 0))
     {
      // Voice出力
      w = chip.ws_ioRam[0x89];
      w -= 0x80;
      l += chip.PCMVolumeLeft * w;
      r += chip.PCMVolumeRight * w;
     }
     else if ((chip.ws_ioRam[(int)wsIORam.SNDMOD.v] & (1 << ch)) != 0)
     {
      if ((ch == 3) && ((chip.ws_ioRam[(int)wsIORam.SNDMOD.v] & 0x80) != 0))
      {
       //Noise

       int Masked, XorReg;

       chip.ws_audio[ch].offset += chip.ws_audio[ch].delta;
       cnt = (byte)(chip.ws_audio[ch].offset >> 16);
       chip.ws_audio[ch].offset &= 0xffff;
       while (cnt > 0)
       {
        cnt--;

        chip.NoiseRng &= noise_bit[chip.NoiseType] - 1;
        if (chip.NoiseRng == 0) chip.NoiseRng = noise_bit[chip.NoiseType] - 1;

        Masked = chip.NoiseRng & noise_mask[chip.NoiseType];
        XorReg = 0;
        while (Masked != 0)
        {
         XorReg ^= Masked & 1;
         Masked >>= 1;
        }
        if (XorReg != 0)
         chip.NoiseRng |= noise_bit[chip.NoiseType];
        chip.NoiseRng >>= 1;
       }

       chip.ws_ioRam[(int)wsIORam.PCSRL.v] = (byte)(chip.NoiseRng & 0xff);
       chip.ws_ioRam[(int)wsIORam.PCSRH.v] = (byte)((chip.NoiseRng >> 8) & 0x7f);

       w = (short)((chip.NoiseRng & 1) != 0 ? 0x7f : -0x80);
       l += chip.ws_audio[ch].lvol * w;
       r += chip.ws_audio[ch].rvol * w;
      }
      else
      {
       chip.ws_audio[ch].offset += chip.ws_audio[ch].delta;
       cnt = (byte)(chip.ws_audio[ch].offset >> 16);
       chip.ws_audio[ch].offset &= 0xffff;
       chip.ws_audio[ch].pos += cnt;
       chip.ws_audio[ch].pos &= 0x1f;
       w = chip.ws_internalRam[(chip.ws_audio[ch].wave & 0xFFF0) + (chip.ws_audio[ch].pos >> 1)];
       if ((chip.ws_audio[ch].pos & 1) == 0)
        w = (short)((w << 4) & 0xf0);    //下位ニブル
       else
        w = (short)(w & 0xf0);           //上位ニブル
       w -= 0x80;
       l += chip.ws_audio[ch].lvol * w;
       r += chip.ws_audio[ch].rvol * w;
      }
     }
    }

    bufL[i] = l * chip.MainVolume;
    bufR[i] = r * chip.MainVolume;
   }
  }

  static void ws_audio_port_write(wsa_state info, byte port, byte value)
  {
   wsa_state chip = (wsa_state)info;
   int i;
   float freq;

   chip.ws_ioRam[port] = value;

   switch (port & 0xff)
   {
    // 0x80-0x87の周波数レジスタについて
    // - ロックマン&フォルテの0x0fの曲では、周波数=0xFFFF の音が不要
    // - デジモンディープロジェクトの0x0dの曲のノイズは 周波数=0x07FF で音を出す
    // →つまり、0xFFFF の時だけ音を出さないってことだろうか。
    //   でも、0x07FF の時も音を出さないけど、ノイズだけ音を出すのかも。
    case 0x80:
    case 0x81:
     i = (int)((((int)chip.ws_ioRam[0x81]) << 8) + ((int)chip.ws_ioRam[0x80]));
     if (i == 0xffff)
      freq = 0;
     else
      freq = 1.0f / (2048 - (i & 0x7ff));
     chip.ws_audio[0].delta = (int)(freq * chip.ratemul);
     break;
    case 0x82:
    case 0x83:
     i = (int)((((int)chip.ws_ioRam[0x83]) << 8) + ((int)chip.ws_ioRam[0x82]));
     if (i == 0xffff)
      freq = 0;
     else
      freq = 1.0f / (2048 - (i & 0x7ff));
     chip.ws_audio[1].delta = (int)(freq * chip.ratemul);
     break;
    case 0x84:
    case 0x85:
     i = (int)((((int)chip.ws_ioRam[0x85]) << 8) + ((int)chip.ws_ioRam[0x84]));
     chip.SweepFreq = i;
     if (i == 0xffff)
      freq = 0;
     else
      freq = 1.0f / (2048 - (i & 0x7ff));
     chip.ws_audio[2].delta = (int)(freq * chip.ratemul);
     break;
    case 0x86:
    case 0x87:
     i = (int)((((int)chip.ws_ioRam[0x87]) << 8) + ((int)chip.ws_ioRam[0x86]));
     if (i == 0xffff)
      freq = 0;
     else
      freq = 1.0f / (2048 - (i & 0x7ff));
     chip.ws_audio[3].delta = (int)(freq * chip.ratemul);
     break;
    case 0x88:
     chip.ws_audio[0].lvol = (byte)((value >> 4) & 0xf);
     chip.ws_audio[0].rvol = (byte)(value & 0xf);
     break;
    case 0x89:
     chip.ws_audio[1].lvol = (byte)((value >> 4) & 0xf);
     chip.ws_audio[1].rvol = (byte)(value & 0xf);
     break;
    case 0x8A:
     chip.ws_audio[2].lvol = (byte)((value >> 4) & 0xf);
     chip.ws_audio[2].rvol = (byte)(value & 0xf);
     break;
    case 0x8B:
     chip.ws_audio[3].lvol = (byte)((value >> 4) & 0xf);
     chip.ws_audio[3].rvol = (byte)(value & 0xf);
     break;
    case 0x8C:
     chip.SweepStep = (byte)value;
     break;
    case 0x8D:
     //Sweepの間隔は 1/375[s] = 2.666..[ms]
     //CPU Clockで言うと 3072000/375 = 8192[cycles]
     //ここの設定値をnとすると、8192[cycles]*(n+1) 間隔でSweepすることになる
     //
     //これを HBlank (256cycles) の間隔で言うと、
     //　8192/256 = 32
     //なので、32[HBlank]*(n+1) 間隔となる
     chip.SweepTime = (short)((((short)value) + 1) << 5);
     chip.SweepCount = chip.SweepTime;
     break;
    case 0x8E:
     chip.NoiseType = (byte)(value & 7);
     if ((value & 8) != 0) chip.NoiseRng = 1;  //ノイズカウンターリセット
     break;
    case 0x8F:
     chip.ws_audio[0].wave = (int)(value << 6);
     chip.ws_audio[1].wave = (int)(chip.ws_audio[0].wave + 0x10);
     chip.ws_audio[2].wave = (int)(chip.ws_audio[1].wave + 0x10);
     chip.ws_audio[3].wave = (int)(chip.ws_audio[2].wave + 0x10);
     break;
    case 0x90://SNDMOD
     break;
    case 0x91:
     //ここでのボリューム調整は、内蔵Speakerに対しての調整だけらしいので、
     //ヘッドフォン接続されていると認識させれば問題無いらしい。
     chip.ws_ioRam[port] |= 0x80;
     break;
    case 0x92:
    case 0x93:
     break;
    case 0x94:
     chip.PCMVolumeLeft = (byte)((value & 0xc) * 2);
     chip.PCMVolumeRight = (byte)(((value << 2) & 0xc) * 2);
     break;
    case 0x52:
     //if (value&0x80)
     // ws_timer_set(2, DMACycles[value&3]);
     break;
   }
  }

  private byte ws_audio_port_read(wsa_state info, byte port)
  {
   wsa_state chip = (wsa_state)info;
   return (chip.ws_ioRam[port]);
  }

  // HBlank間隔で呼ばれる
  // Note: Must be called every 256 cycles (3072000 Hz clock), i.e. at 12000 Hz
  private void ws_audio_process(wsa_state chip)
  {
   float freq;

   if (chip.SweepStep != 0 && (chip.ws_ioRam[(int)wsIORam.SNDMOD.v] & 0x40) != 0)
   {
    if (chip.SweepCount < 0)
    {
     chip.SweepCount = chip.SweepTime;
     chip.SweepFreq += (int)chip.SweepStep;
     chip.SweepFreq &= 0x7FF;

     freq = 1.0f / (2048 - chip.SweepFreq);
     chip.ws_audio[2].delta = (int)(freq * chip.ratemul);
    }
    chip.SweepCount--;
   }
  }

  private void ws_audio_sounddma(wsa_state chip)
  {
   int i;
   int j;
   byte b;

   if ((chip.ws_ioRam[(int)wsIORam.SDMACTL.v] & 0x88) == 0x80)
   {
    i = (int)((chip.ws_ioRam[(int)wsIORam.SDMACH.v] << 8) | chip.ws_ioRam[(int)wsIORam.SDMACL.v]);
    j = (int)((chip.ws_ioRam[(int)wsIORam.SDMASB.v] << 16) | (chip.ws_ioRam[(int)wsIORam.SDMASH.v] << 8) | chip.ws_ioRam[(int)wsIORam.SDMASL.v]);
    //b=cpu_readmem20(j);
    b = chip.ws_internalRam[j & 0x3FFF];

    chip.ws_ioRam[0x89] = b;
    i--;
    j++;
    if (i < 32)
    {
     i = 0;
     chip.ws_ioRam[(int)wsIORam.SDMACTL.v] &= 0x7F;
    }
    else
    {
     // set DMA timer
     //ws_timer_set(2, DMACycles[SDMACTL&3]);
    }
    chip.ws_ioRam[(int)wsIORam.SDMASB.v] = (byte)((j >> 16) & 0xFF);
    chip.ws_ioRam[(int)wsIORam.SDMASH.v] = (byte)((j >> 8) & 0xFF);
    chip.ws_ioRam[(int)wsIORam.SDMASL.v] = (byte)(j & 0xFF);
    chip.ws_ioRam[(int)wsIORam.SDMACH.v] = (byte)((i >> 8) & 0xFF);
    chip.ws_ioRam[(int)wsIORam.SDMACL.v] = (byte)(i & 0xFF);
   }
  }

  private void ws_write_ram_byte(wsa_state info, int offset, byte value)
  {
   wsa_state chip = (wsa_state)info;

   // RAM - 16 KB (WS) / 64 KB (WSC) internal RAM
   chip.ws_internalRam[offset & 0x3FFF] = value;
   return;
  }

  private byte ws_read_ram_byte(wsa_state info, int offset)
  {
   wsa_state chip = (wsa_state)info;

   return chip.ws_internalRam[offset & 0x3FFF];
  }

  private void ws_set_mute_mask(wsa_state info, int MuteMask)
  {
   wsa_state chip = (wsa_state)info;
   byte CurChn;

   for (CurChn = 0; CurChn < 4; CurChn++)
    chip.ws_audio[CurChn].Muted = (byte)((MuteMask >> CurChn) & 0x01);

   return;
  }

  private int ws_get_mute_mask(wsa_state info)
  {
   wsa_state chip = (wsa_state)info;
   int muteMask;
   byte CurChn;

   muteMask = 0x00;
   for (CurChn = 0; CurChn < 4; CurChn++)
    muteMask |= (int)(chip.ws_audio[CurChn].Muted << CurChn);

   return muteMask;
  }

    }
