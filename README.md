[![Release](https://jitpack.io/v/umjammer/vavi-sound-mdsound.svg)](https://jitpack.io/#umjammer/vavi-sound-mdsound)
[![Java CI](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/codeql.yml/badge.svg)](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/codeql.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)

# vavi-sound-mdsound

Video Game Music Emulation Library

this is a fork of [MDsound](https://github.com/kuma4649/MDSound)

### Status

| name                        | common name | type  | status | comment                  | sample                                                                                                                       |
|-----------------------------|-------------|-------|:------:|--------------------------|------------------------------------------------------------------------------------------------------------------------------|
| YM2612 (mame-A)             | OPN2        | FM    |   ✅️   | mame:dallongeville+green | Puyo Puyo/09 - Sticker.vgz                                                                                                   |
| YM2612 (mame-B)             | OPN2        | FM    |   ✅️   | mame:burczynski          | Puyo Puyo/09 - Sticker.vgz                                                                                                   |
| YM3438                      | OPN2 (cmos) | FM    |   🚧   | nukeykt                  |                                                                                                                              |
| YM2151 (mame)               | OPM         | FM    |   ✅️   | mame                     | Out_Run_(Arcade)/01 Magical Sound Shower.vgz                                                                                 |
| YM2151 (fmgen)              | OPM         | FM    |   ✅️   | fmgen                    | ditto                                                                                                                        |
| YM2151 (x68sound)           | OPM         | FM    |   ✅️   | x68sound                 | ditto                                                                                                                        |
| YM2203                      | OPN         | FM    |   ✅    | fmgen                    | Space_Harrier_(Hang-On)/02 Theme.vgz                                                                                         |
| YM2608                      | OPNA        | FM    |   ✅    | fmgen                    | mucom88.mub<br/>Touhou_Reiiden_~_Highly_Responsive_to_Prayers._(NEC_PC-9801)/01 A Sacred Lot.vgz                             |
| YM2610/B                    | OPNB        | FM    |   ✅    | fmgen                    |                                                                                                                              |
| YM2413                      | OPLL        | FM    |   ✅    | okaxaki                  |                                                                                                                              |
| YM3526                      | OPL         | FM    |   ✅    | burczynski               |                                                                                                                              |
| YM3812 (dosbox)<sup>*</sup> | OPL2        | FM    |   ✅    | dosbox                   |                                                                                                                              |
| YM3812 (mame)<sup>*</sup>   | OPL2        | FM    |   ✅    | burczynski               |                                                                                                                              |
| Y8950                       | OPL2+ADPCM  | FM    |   🚧   | mame                     |                                                                                                                              |
| YMF262                      | OPL3        | FM    |  ✅ 🚧  | dosbox                   | Touhou_Koumakyou_~_the_Embodiment_of_Scarlet_Devil._(IBM_PC_AT)/05 Tomboyish Girl in Love (Stage 2 Boss - Cirno's Theme).vgz |
| YMF262                      | OPL3        | FM    |   ✅    | mame                     | Touhou_Koumakyou_~_the_Embodiment_of_Scarlet_Devil._(IBM_PC_AT)/05 Tomboyish Girl in Love (Stage 2 Boss - Cirno's Theme).vgz |
| YMF278B                     | OPL4        | FM    |   ✅    | galibert                 |                                                                                                                              |
| RF5C164                     | RF5C        | PCM   |   ✅    |                          | Sonic CD (Mega CD)/01 - Palmtree Panic Zone Past.vgz                                                                         |
| PWM                         | PWM         | PCM   |   🚧   |                          | Virtua_Fighter_(Sega_32X)/04 Theme of Jacky.vgz                                                                              |
| C140                        | C140        | PCM   |   ✅    | mame                     | Dragon_Saber_-_After_Story_of_Dragon_Spirit_(Namco_System_2)/04 Submerged City (Stage 1).vgz                                 |
| C352                        | C352        | PCM   |   ✅️   |                          | Ridge_Racer_(Namco_System_22)/03 Rare Hero (Sanodigy mix).vgz                                                                |
| OKIM6258                    | OKI65       | PCM   |   ✅️   |                          | Akumajo_Dracula_(Sharp_X68000)/02 Black Mass (Opening).vgz                                                                   |
| MPCM (OKIM6258)             | MPCM        | PCM   |        | mndrv                    |                                                                                                                              |
| OKIM6295                    | OKI69       | PCM   |   ✅    | mame                     | Street_Fighter_II_-_Champion_Edition_(CP_System)/05 Japan (Ryu) I.vgz                                                        |
| SEGAPCM                     | SEGAPCM     | PCM   |   ✅️   |                          | Out_Run_(Arcade)/01 Magical Sound Shower.vgz                                                                                 |
| K005289                     | K005        | PCM   |  n/a️  | mame                     |                                                                                                                              |
| K051649                     | K051        | PCM   |   ✅️   | mame                     | Salamander_(MSX)/02 Power of Anger.vgz                                                                                       |
| K053260                     | K053        | PCM   |   ✅️   | mame                     | Sunset_Riders_(Konami_Sunset_Riders)/05 Shoot-out at the Sunset Ranch (1, 5, 8 Stage BGM).vgz                                |
| K054539                     | K054        | PCM   |   ✅️   | mame                     | X-Men_(Arcade)/05 Here Comes The Hero (Stage 1).vgz                                                                          |
| NES_DMC                     |             | PCM   |        |                          |                                                                                                                              |
| PPZ8                        | PPZ8        | PCM   |        |                          |                                                                                                                              |
| PPS                         | PPS         | PCM   |        |                          |                                                                                                                              |
| PC-9801-86                  | P86         | PCM   |        |                          |                                                                                                                              |
| HuC6280 (has FM-like)       | HuC6        | WTS   |   ✅    |                          | Street_Fighter_II'_-_Champion_Edition_(TG-16)/04 Ryu.vgz                                                                     |
| K051649                     | K051        | WTS   |   ✅    |                          | opllssg_demo_vgm/GIMICNRT.vgm                                                                                                |
| NES_FDS (has FM-like)       |             | WTS   |        |                          |                                                                                                                              |
| SN76489                     |             | PSG   |   ✅    | nicola                   | Thexder_(IBM_PCjr,_Tandy_1000)/02 Thexder Theme \[IBM PCjr].vgz                                                              |
| SN76496                     |             | PSG   |   ✅    | nicola                   | ditto                                                                                                                        |
| AY8910                      |             | PSG   |   ✅    | fmgen                    | opllssg_demo_vgm/CS3.vgm                                                                                                     |
| AY8910 (mame)               |             | PSG   |        | mame                     |                                                                                                                              |
| NES_APU                     |             | PSG   |   🚧   |                          |                                                                                                                              |
| YM2609                      | OPNA2       | Other |        |                          |                                                                                                                              |
| AY8910-2                    | PSG2        | Other |        | fmvgen                   |                                                                                                                              |
| QSound<sup>*</sup>          |             | PCM?  |   ✅    |                          | Street_Fighter_EX_(ZN-1)/04 Rising Dragoon.vgz                                                                               |
| QSound (ctr)                |             | PCM?  |   ✅    |                          | Street_Fighter_EX_(ZN-1)/04 Rising Dragoon.vgz                                                                               |
| YmF271                      |             | FM    |   🚧   |                          |                                                                                                                              |
| YmZ280B                     |             | FM    |   🚧   |                          |                                                                                                                              |
| Dmg                         |             |       |   🚧   |                          |                                                                                                                              |
| Ga20                        |             |       |   ✅    |                          | R-Type_Leo_(Irem_M92)/02 Paradise Planet (Area 1).vgz                                                                        |
| MultiPcm                    |             | PCM   |   🚧   |                          |                                                                                                                              |
| Pokey                       |             |       |   ✅    | mame                     | Xevious_(Atari_5200)/01 Start ~ Main BGM.vgz                                                                                 |
| Rf5C68                      |             | PCM   |   ✅    |                          | Michael_Jackson's_Moonwalker_(Sega_System_18)/04 Smooth Criminal (Round 2).vgz                                               |
| Saa1099                     |             |       |   ✅    |                          | Creative_Music_System_Demo_Songs_(IBM_PC_XT_AT)/02 Top of the World (The Carpenters, A Song for You).vgz                     |
| ScdPcm                      |             | PCM   |        |                          |                                                                                                                              |
| Vrc6                        |             |       |        |                          |                                                                                                                              |
| WSwan                       |             |       |   🚧   |                          |                                                                                                                              |
| X1_010                      |             |       |        |                          |                                                                                                                              |

<sub>* WTS ... [wavetable synthesis](https://ja.wikipedia.org/wiki/%E6%B3%A2%E5%BD%A2%E3%83%A1%E3%83%A2%E3%83%AA%E9%9F%B3%E6%BA%90)</sub><br/>
<sub>* YM3812 ... switched internally (TODO separate instrument)</sub><br/>
<sub>* QSound ... unused (TODO settings)</sub>

#### Settings

| chip              | setting                      |
|-------------------|------------------------------|
| YM2151 (mame)     | YM2151Type.UseEmu[0] = true  |
| YM2151 (fmgen)    | YM2151Type.UseEmu[1] = true  |
| YM2151 (x68sound) | YM2151Type.UseEmu[2] = true  |
| YM2151 (ymfm)     | YM2151Type.UseEmu[3] = true  |
| YM2203 (ymfm)     | YM2203Type.UseEmu[1] = true  |
| YM2162 (mame-A)   | YM2612Type.UseEmu[0] = true  |
| YM2162 (nuked)    | YM2612Type.UseEmu[1] = true  |
| YM2162 (mame-B)   | YM2612Type.UseEmu[2] = true  |
| SN76496           | SN76496Type.UseEmu[0] = treu |
| SN76489           | SN76496Type.UseEmu[1] = treu |

## Install

* [maven](https://jitpack.io/#umjammer/vavi-sound-mdsound)

## Usage

 * test is still wip, use via [vavi-apps-mdplayer]()

## References

* https://github.com/fedex81/simplevgm
* https://github.com/io7m/jvgm
* https://github.com/dksrphm/jvgmtrans
* https://github.com/MehVahdJukaar/Moonlight
* https://github.com/ShreyasTheRag/PSG-audio
* https://github.com/vampirefrog/fmtoy
* https://github.com/nukeykt/Nuked-OPN2
* https://en.wikipedia.org/wiki/Advanced_Multimedia_Supplements (QSound related)

## TODO

 * test all
 * javax.sound.sampled.spi, javax.sound.sampled.midi.spi
 * debug
   * ymf262(dosbox) ssg? taste is wrong
   * y8950 rhythm only?

---

# [Original](https://github.com/kuma4649/MDSound)

## Overview

This library is a port of the operation of the following sound source chips installed in Mega Drive etc.
from the source of VGM Player etc. to the code for Java.

FM sound source

- YM2612 OPN2
- YM3438 OPN2 (cmos)
- YM2151 (mame) OPM
- YM2151 (FMGEN) OPM
- YM2151 (X68sound) OPM
- YM2203 OPN
- YM2608 OPNA
- YM2610 / B OPNB
- YM2413 OPLL
- YMF262 OPL3
- YMF278B OPL4

PCM sound source

- RF5C164 RF5C
- PWM PWM
- C140 C140
- OKIM6258 OKI65
- MPCM (OKIM6258) MPCM
- OKIM6295 OKI69
- SEGAPCM SEGAPCM
- C352 C352
- K054539 K054
- NES_DMC
- PPZ8 PPZ8
- PPSDRV PPSDRV
- PC-9801-86 P86

Waveform memory sound source

- HuC6280 (FM-like individual ownership) HuC6
- K051649 K051
- NES_FDS (FM-like individual ownership)

PSG sound source

- SN76489
- AY8910
- NES_APU

Other (virtual sound source)

- YM2609 OPNA2
- AY8910-2 PSG2

## Copyright / Disclaimer

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

## Special Thanks

This tool is indebted to the following people. We also refer to and use the following software and web pages.

thank you very much.

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
 - Making VST with C++
 - Wikipedia

 - [Original in C#](https://github.com/kuma4649/MDSound)
