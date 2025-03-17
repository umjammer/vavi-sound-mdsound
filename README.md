[![Release](https://jitpack.io/v/umjammer/vavi-sound-mdsound.svg)](https://jitpack.io/#umjammer/vavi-sound-mdsound)
[![Java CI](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/codeql.yml/badge.svg)](https://github.com/umjammer/vavi-sound-mdsound/actions/workflows/codeql.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)

# vavi-sound-mdsound

Video Game Music Emulation Library

this is a fork of [MDSound](https://github.com/kuma4649/MDSound)

### Status

| name                  | common name | type  | status | origin                   | sample                                                                                                                       |
|-----------------------|-------------|-------|:------:|--------------------------|------------------------------------------------------------------------------------------------------------------------------|
| YM2612 (mame:A) gens? | OPN2        | FM    |   ✅️   | mame:dallongeville+green | Puyo Puyo/09 - Sticker.vgz                                                                                                   |
| YM2612 (mame:B)       | OPN2        | FM    |   ✅️   | mame:burczynski          | ditto                                                                                                                        |
| YM3438                | OPN2 (cmos) | FM    |   ✅️   | nukeykt:A+bufA           | ditto                                                                                                                        |
| YM3438 (simple)       | OPN2 (cmos) | FM    |   ✅️   | nukeykt:B+bufB           | ditto                                                                                                                        |
| YM3438 (vavi)         | OPN2 (cmos) | FM    |   ✅️   | nukeykt:A+bufB           | ditto                                                                                                                        |
| YM2151 (mame)         | OPM         | FM    |   ✅️   | mame                     | Out_Run_(Arcade)/01 Magical Sound Shower.vgz                                                                                 |
| YM2151 (fmgen)        | OPM         | FM    |   ✅️   | fmgen                    | ditto                                                                                                                        |
| YM2151 (x68sound)     | OPM         | FM    |   ✅️   | x68sound                 | ditto                                                                                                                        |
| YM2151 (ymfm)         | OPM         | FM    |   ✅️   | ymfm                     | ditto                                                                                                                        |
| YM2203                | OPN         | FM    |   ✅    | fmgen                    | Space_Harrier_(Hang-On)/02 Theme.vgz                                                                                         |
| YM2203 (ymfm)         | OPN         | FM    |   ✅    | ymfm                     | ditto                                                                                                                        |
| YM2608                | OPNA        | FM    |   ✅    | fmgen                    | mucom88.mub<br/>Touhou_Reiiden_~_Highly_Responsive_to_Prayers._(NEC_PC-9801)/01 A Sacred Lot.vgz                             |
| YM2608 (ymfm)         | OPNA        | FM    |   ✅    | ymfm                     | ditto                                                                                                                        |
| YM2610/B              | OPNB        | FM    |   ✅    | fmgen                    | F-1_Grand_Prix_Part_II_(Arcade)/01 Truth.vgz                                                                                 |
| YM2610 (ymfm)         | OPNB        | FM    |   ✅    | ymfm                     | ditto                                                                                                                        |
| YM2413                | OPLL        | FM    |   ✅    | okaxaki                  | opllssg_demo_vgm/nrt6.vgm                                                                                                    |
| YM2413 (emu)          | OPLL        | FM    |   ✅    | okaxaki                  | ditto                                                                                                                        |
| YM3526                | OPL         | FM    |   ✅    | oldmame:burczynski       | Terra Cresta - 02 - Theme of Terracresta.vgm                                                                                 |
| YM3812 (dosbox)       | OPL2        | FM    |   ✅    | dosbox                   | Out_Zone_(Toaplan_1)/05 Soldier a GoGo (Chapter 4).vgz                                                                       |
| YM3812 (mame)         | OPL2        | FM    |   ✅    | oldmame:burczynski       | ditto                                                                                                                        |
| Y8950                 | OPL2+ADPCM  | FM    |   ✅    | oldmame:burczynski       | Impact_MuSiX_Disk_#2_(MSX2)/04 Moment for Morricone.vgz                                                                      |
| YMF262 (dosbox)       | OPL3        | FM    |   ✅    | dosbox                   | Touhou_Koumakyou_~_the_Embodiment_of_Scarlet_Devil._(IBM_PC_AT)/05 Tomboyish Girl in Love (Stage 2 Boss - Cirno's Theme).vgz |
| YMF262 (mame)         | OPL3        | FM    |   ✅    | mame                     | ditto                                                                                                                        |
| YMF262 (nuked)        | OPL3        | FM    |   ✅    | nukeykt                  | ditto                                                                                                                        |
| YMF262 (cozendey)     | OPL3        | FM    |   ✅    | cozendey                 | ditto                                                                                                                        |
| YMF262 (ymfm)         | OPL3        | FM    |   ✅    | ymfm                     | ditto                                                                                                                        |
| YMF278B               | OPL4        | FM    |   ✅    | galibert                 | MoonDriver_Demo_(MSX2+)/05 encounter the unknown environment.vgz                                                             |
| YMF271                | OPX         | FM    |   ✅    |                          | Desert_War_(Jaleco_Mega_System_32)/08 Destruction Mission.vgz                                                                |
| YMZ280B               |             | FM    |   ✅    |                          | Guwange_(Cave_68000)/03 Falling Cherry Blossoms.vgz                                                                          |
| RF5C164               | ScdPcm      | PCM   |   ✅    |                          | Sonic CD (Mega CD)/01 - Palmtree Panic Zone Past.vgz                                                                         |
| RF5C68                | RF5C        | PCM   |   ✅    |                          | Michael_Jackson's_Moonwalker_(Sega_System_18)/04 Smooth Criminal (Round 2).vgz                                               |
| C140 (system2)        | C140        | PCM   |   ✅    | mame:A                   | Dragon_Saber_-_After_Story_of_Dragon_Spirit_(Namco_System_2)/04 Submerged City (Stage 1).vgz                                 |
| C140 (system21)       | C140        | PCM   |   ✅    | mame:A                   | Cyber_Sled_(Namco_System_21)/06 Be Warped Time.vgz                                                                           |
| C140 (asicC219)       | C219        | PCM   |   ✅    | mame:A                   | Knuckle_Heads_(Namco_NA-2)/04 Hawk's Dance, Wolf's Howl (VS Fujioka).vgz                                                     |
| C219                  | C219        | PCM   |   ✅    | mame:B                   | ditto                                                                                                                        |
| C352                  | C352        | PCM   |   ✅️   |                          | Ridge_Racer_(Namco_System_22)/03 Rare Hero (Sanodigy mix).vgz                                                                |
| OKIM6258              | OKI65       | PCM   |   ✅️   |                          | Akumajo_Dracula_(Sharp_X68000)/02 Black Mass (Opening).vgz                                                                   |
| MPCM (OKIM6258)       | MPCM        | PCM   |        | mndrv                    |                                                                                                                              |
| OKIM6295              | OKI69       | PCM   |   ✅    | mame                     | Street_Fighter_II_-_Champion_Edition_(CP_System)/05 Japan (Ryu) I.vgz                                                        |
| SEGAPCM               | SEGAPCM     | PCM   |   ✅️   |                          | Out_Run_(Arcade)/01 Magical Sound Shower.vgz                                                                                 |
| K005289               | K005        | PCM   |  n/a️  | mame                     |                                                                                                                              |
| K051649               | K051        | PCM   |   ✅️   | mame                     | Salamander_(MSX)/02 Power of Anger.vgz                                                                                       |
| K053260               | K053        | PCM   |   ✅️   | mame                     | Sunset_Riders_(Konami_Sunset_Riders)/05 Shoot-out at the Sunset Ranch (1, 5, 8 Stage BGM).vgz                                |
| K054539               | K054        | PCM   |   ✅️   | mame                     | X-Men_(Arcade)/05 Here Comes The Hero (Stage 1).vgz                                                                          |
| QSound                |             | PCM   |   ✅    |                          | Street_Fighter_EX_(ZN-1)/04 Rising Dragoon.vgz                                                                               |
| QSound (ctr)          |             | PCM   |   ✅    |                          | ditto                                                                                                                        |
| PWM                   | PWM         | PCM   |   ✅    |                          | Virtua_Fighter_(Sega_32X)/04 Theme of Jacky.vgz                                                                              |
| MultiPcm              |             | PCM   |   ✅    |                          | Daytona_USA_(Sega_Model_2)/03 The King of Speed.vgz                                                                          |
| WSwan                 |             | PCM   |   ✅    |                          | Final_Fantasy_(Bandai_WonderSwan_Color)/02 Prelude.vgz                                                                       |
| X1_010                |             | PCM   |   ✅    |                          | Cal.50_Caliber_Fifty_(Seta_1)/02 Area 1.vgz                                                                                  |
| NES_DMC               |             | PCM   |        | nes                      |                                                                                                                              |
| PPZ8                  | PPZ8        | PCM   |        | pmd                      |                                                                                                                              |
| PPS                   | PPS         | PCM   |        | pmd                      |                                                                                                                              |
| PC-9801-86            | P86         | PCM   |        | pmd                      |                                                                                                                              |
| HuC6280 (has FM-like) | HuC6        | WTS   |   ✅    |                          | Street_Fighter_II'_-_Champion_Edition_(TG-16)/04 Ryu.vgz                                                                     |
| K051649               | K051        | WTS   |   ✅    |                          | opllssg_demo_vgm/GIMICNRT.vgm                                                                                                |
| NES_FDS (has FM-like) |             | WTS   |        | nes                      |                                                                                                                              |
| SN76489               | DSCG        | PSG   |   ✅    | nicola                   | Thexder_(IBM_PCjr,_Tandy_1000)/02 Thexder Theme \[IBM PCjr].vgz                                                              |
| SN76496               |             | PSG   |   ✅    | nicola                   | ditto                                                                                                                        |
| AY8910                |             | PSG   |   ✅    | fmgen                    | opllssg_demo_vgm/CS3.vgm                                                                                                     |
| AY8910 (mame)         |             | PSG   |   ✅    | mame                     | NRTDRV_Demo_Songs_(NEC_PC-8801)/14 Perfume - Electro World.vgz                                                               |
| Ga20                  |             | PSG?  |   ✅    | mame                     | R-Type_Leo_(Irem_M92)/02 Paradise Planet (Area 1).vgz                                                                        |
| Pokey                 |             | PSG?  |   ✅    | mame                     | Xevious_(Atari_5200)/01 Start ~ Main BGM.vgz                                                                                 |
| Saa1099               |             | PSG?  |   ✅    | mame                     | Creative_Music_System_Demo_Songs_(IBM_PC_XT_AT)/02 Top of the World (The Carpenters, A Song for You).vgz                     |
| NES_APU               |             | PSG   |   🚧   | nes                      |                                                                                                                              |
| YM2609                | OPNA2       | Other |        | fmvgen                   |                                                                                                                              |
| AY8910-2              | PSG2        | Other |        | fmvgen                   |                                                                                                                              |
| Dmg                   |             |       |   ✅    | dmg                      | Beatmania_GB2_Gatcha_Mix_(Game_Boy,_Color)/11 Friends.vgz                                                                    |
| Vrc6                  |             |       |        | nes                      |                                                                                                                              |
| YM2149                |             | PSG   |   🥚   | np:okaxaki               |                                                                                                                              |
| YM2143                | OPLL        | FM    |   🥚   | np:okaxaki               |                                                                                                                              |
| Gigatron              |             |       |        | zgm                      |                                                                                                                              |
| MPCMPP                |             | PCM   |        | mnd                      |                                                                                                                              |
| PCM8PP                |             | PCM   |   ✅?   | mxdrv                    |                                                                                                                              |
| MSM5232               |             |       |   🥚   |                          |                                                                                                                              |
| ES5503                |             |       |   ✅    |                          | Space_Fox_(Apple_IIgs)/01 Title.vgz                                                                                          |
| ES5505                |             |       |   🥚   |                          |                                                                                                                              |
| μPD7759               |             | PCM   |   🥚   |                          |                                                                                                                              |

<sub>* WTS ... [wavetable synthesis](https://ja.wikipedia.org/wiki/%E6%B3%A2%E5%BD%A2%E3%83%A1%E3%83%A2%E3%83%AA%E9%9F%B3%E6%BA%90)</sub><br/>

## Install

* [maven](https://jitpack.io/#umjammer/vavi-sound-mdsound)

## Usage

 * test is still wip, use via [vavi-apps-mdplayer](https://jitpack.io/#umjammer/vavi-apps-mdplayer)

## References

* https://github.com/fedex81/simplevgm
* https://github.com/io7m/jvgm
* https://github.com/dksrphm/jvgmtrans
* https://github.com/MehVahdJukaar/Moonlight
* https://github.com/ShreyasTheRag/PSG-audio
* https://github.com/vampirefrog/fmtoy
* https://github.com/nukeykt/Nuked-OPN2
* https://en.wikipedia.org/wiki/Advanced_Multimedia_Supplements (QSound related)
* https://github.com/Gnzdream/NsfPlayer
* https://sakuramail.net/fswold/music.html#muskin (MuSICA related)
* libvgm says
   * ym3812 dosbox is better than mame
   * ym2612 nuked uses a lot of cpu
* https://github.com/M-HT/websynth_d-77 (ps2 wavesynth)
* https://github.com/M-HT/casio_sw-10 (wavesynth (not vgm))
* https://sourceforge.net/projects/vgmtoolbox/

## TODO

 * test all
 * javax.sound.sampled.spi, javax.sound.midi.spi
 * debug
   * ~~ymf262 (dosbox) no ssg? or so small~~
   * ~~ymf262 (nuked) no sound -> small click sound, and lack of channels???~~
   * ~~ymf262 (cozendey) some notes lacked? sampling related?~~
   * ~~y8950 rhythm only?~~
   * ~~YM2151 (x68sound) noise (05 Japan (Ryu) I.vgz)~~
   * ~~YmF271 mostly~~
   * ~~YM3438 (nuke) no sound chip impl works (vavi), but buffered writing not works~~
   * ~~multipcm mostly~~
   * ~~wswan noise only~~
   * ~~pwn laud noise~~
   * ~~ymf262 (nuked) little bit different?~~

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

- HuC6280 (has FM-like) HuC6
- K051649 K051
- NES_FDS (has FM-like)

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
