[![Release](https://jitpack.io/v/umjammer/vavi-sound-mdsound.svg)](https://jitpack.io/#umjammer/vavi-sound-mdsound)
[![Java CI](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/codeql.yml/badge.svg)](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/codeql.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)

# vavi-sound-mdsound

Video Game Music Emulation Library

this is a fork of [MDsound](https://github.com/kuma4649/MDSound)

### Overview  

This library is a port of the operation of the following sound source chips installed in Mega Drive etc.
from the source of VGM Player etc. to the code for Java.

FM sound source

| name                                   | common name | status | comment                                      | sample                                                                |
|----------------------------------------|-------------|:------:|----------------------------------------------|-----------------------------------------------------------------------|
| YM2612                                 | OPN2        |   ✅️   |                                              | Puyo Puyo/09 - Sticker.vgz                                            |
| YM3438                                 | OPN2 (cmos) |        |                                              |                                                                       |
| YM2151 (mame)                          | OPM         |   ✅️   | YM2151Type.UseEmu[0] = true                  | Out_Run_(Arcade)/01 Magical Sound Shower.vgz                          |
| YM2151 (FMGEN)                         | OPM         |   ✅️   | YM2151Type.UseEmu[1] = true                  | ditto                                                                 |
| YM2151 (X68sound)                      | OPM         |   ✅️   | YM2151Type.UseEmu[2] = true                  | ditto                                                                 |
| YM2203                                 | OPN         |   🚫   | ymfm ✅: YM2203Type.UseEmu[1] = true          | Space_Harrier_(Hang-On)/02 Theme.vgz                                  |
| YM2608                                 | OPNA        |   ✅    | ymfm 🚫:                                     | mucom88.mub                                                           |
| YM2610/B                               | OPNB        |        |                                              |                                                                       |
| YM2413                                 | OPLL        |        |                                              |                                                                       |
| YMF262                                 | OPL3        |        |                                              |                                                                       |
| YMF278B                                | OPL4        |        |                                              |                                                                       |
| PCM sound source                       |             |        |                                              |                                                                       |
| RF5C164                                | RF5C        |        |                                              |                                                                       |
| PWM                                    | PWM         |        |                                              |                                                                       |
| C140                                   | C140        |        |                                              |                                                                       |
| OKIM6258                               | OKI65       |        |                                              |                                                                       |
| MPCM (OKIM6258)                        | MPCM        |        |                                              |                                                                       |
| OKIM6295                               | OKI69       |   ✅    |                                              | Street_Fighter_II_-_Champion_Edition_(CP_System)/05 Japan (Ryu) I.vgz |
| SEGAPCM                                | SEGAPCM     |   ✅️   | 🐛 some timing is wrong? vgm? (outrun mss →) | Out_Run_(Arcade)/01 Magical Sound Shower.vgz                          |
| C352                                   | C352        |        |                                              |                                                                       |
| K054539                                | K054        |        |                                              |                                                                       |
| NES_DMC                                |             |        |                                              |                                                                       |
| PPZ8                                   | PPZ8        |        |                                              |                                                                       |
| PPSDRV                                 | PPSDRV      |        |                                              |                                                                       |
| PC-9801-86                             | P86         |        |                                              |                                                                       |
| Waveform memory sound source           |             |        |                                              |                                                                       |
| HuC6280 (FM-like individual ownership) | HuC6        |   ✅    |                                              | Street_Fighter_II'_-_Champion_Edition_(TG-16)/04 Ryu.vgz              |
| K051649                                | K051        |        |                                              |                                                                       |
| NES_FDS (FM-like individual ownership) |             |        |                                              |                                                                       |
| PSG sound source                       |             |        |                                              |                                                                       |
| SN76489                                |             |        |                                              |                                                                       |
| AY8910                                 |             |        |                                              |                                                                       |
| NES_APU                                |             |        |                                              |                                                                       |
| Other (virtual sound source)           |             |        |                                              |                                                                       |
| YM2609                                 | OPNA2       |        |                                              |                                                                       |
| AY8910-2                               | PSG2        |        |                                              |                                                                       |

### Copyright / Disclaimer

vavi-sound-mdsound is free software. The copyright is owned by the author.
This software is not guaranteed and is due to the use of this software
The author does not take any responsibility for any damage.
The license shall be in accordance with the LGPL license.

vavi-sound-mdsound is porting and using the following software source code for Java.
These sources are copyrighted by their respective authors.
Please refer to each document for the license.

 - VGMPlay
 - MAME
 - Gens
 - Ootake
 - Fmgen
 - NSFPlay
 - X68Sound.dll
 - TinyMPCM (provisional)
 - Nuked-OPN2
 - PMDWin
 - Filter and effector by Kazura Utsubo

### Special Thanks

This tool is indebted to the following people. We also refer to and use the following software and web pages.

thank you very much.

 - Visual Studio Community 2015
 - SGDK
 - VGM Player
 - Nuked-OPN2
 - Git
 - SDL/SDLNET
 - SourceTree
 - Sakura Editor
 - QUASI88 document
 - SMS Power!
 - DOBON.NET
 - Making VST with C ++
 - Wikipedia
 - [Original in C#](https://github.com/kuma4649/MDSound)

## Install

 * [maven](https://jitpack.io/#umjammer/vavi-sound-mdsound)

## Usage

## References

 * https://github.com/fedex81/simplevgm
 * https://github.com/io7m/jvgm
 * https://github.com/dksrphm/jvgmtrans
 * https://github.com/MehVahdJukaar/Moonlight
 * https://github.com/ShreyasTheRag/PSG-audio
 * https://github.com/vampirefrog/fmtoy
 * https://github.com/nukeykt/Nuked-OPN2

## TODO

 * test all
