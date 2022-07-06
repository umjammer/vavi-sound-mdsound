package mdsound.fmvgen;


import mdsound.fmvgen.effect.ReversePhase;


public class AdpcmB {
    public OPNA2 parent = null;

    public static boolean NO_BITTYPE_EMULATION = false;

    public int stMask;
    public int statusNext;

    // ADPCM RAM
    public byte[] adpcmBuf;
    // メモリアドレスに対するビットマスク
    public int adpcmMask;
    // ADPCM 再生終了時にたつビット
    public int adpcmNotice;
    // Start address
    protected int startAddr;
    // Stop address
    protected int stopAddr;
    // 再生中アドレス
    public int memAddr;
    // Limit address/mask
    protected int limitAddr;
    // ADPCM 音量
    public int adpcmLevel;
    public int adpcmVolume;
    public int adpcmVol;
    // ⊿N
    public int deltaN;
    // 周波数変換用変数
    public int adplC;
    // 周波数変換用変数差分値
    public int adplD;
    // adpld の元
    public int adplBase;
    // ADPCM 合成用 x
    public int adpcMx;
    // ADPCM 合成用 ⊿
    public int adpcmD;
    // ADPCM 合成後の出力
    protected int adpcmOut;
    // out(t-2)+out(t-1)
    protected int apOut0;
    // out(t-1)+out(t)
    protected int apOut1;

    //メモリ
    public int shiftBit = 6;

    protected int status;

    // ADPCM リード用バッファ
    protected int adpcmReadBuf;
    // ADPCM 再生中
    public boolean adpcmPlay;
    protected byte granuality;
    public boolean adpcmMask_;

    // ADPCM コントロールレジスタ１
    protected byte control1;
    // ADPCM コントロールレジスタ２
    public byte control2;
    // ADPCM レジスタの一部分
    protected byte[] adpcmReg = new byte[8];
    protected float panL = 1.0f;
    protected float panR = 1.0f;
    private Fmvgen.Effects effects;
    private int efcCh;
    private int num;

    public AdpcmB(int num, Fmvgen.Effects effects, int efcCh) {
        this.num = num;
        this.effects = effects;
        this.efcCh = efcCh;
    }

    public void mix(int[] dest, int count) {
        int maskL = (control2 & 0x80) != 0 ? -1 : 0;
        int maskR = (control2 & 0x40) != 0 ? -1 : 0;
        if (adpcmMask_) {
            maskL = maskR = 0;
        }

        if (adpcmPlay) {
            int ptrDest = 0;
            //  LOG2("ADPCM Play: %d   DeltaN: %d\n", adpld, deltan);
            if (adplD <= 8192) { // fplay < fsamp
                for (; count > 0; count--) {
                    if (adplC < 0) {
                        adplC += 8192;
                        decode();
                        if (!adpcmPlay)
                            break;
                    }

                    int s = (adplC * apOut0 + (8192 - adplC) * apOut1) >> 13;
                    int[] sL = new int[] {s & maskL};
                    int[] sR = new int[] {s & maskR};
                    effects.distortion.mix(efcCh, sL, sR);
                    effects.chorus.mix(efcCh, sL, sR);
                    effects.hpflpf.mix(efcCh, sL, sR);
                    effects.compressor.mix(efcCh, sL, sR);

                    sL[0] = (int) (sL[0] * panL) * ReversePhase.adpcm[num][0];
                    sR[0] = (int) (sR[0] * panR) * ReversePhase.adpcm[num][1];
                    int revSampleL = (int) (sL[0] * effects.reverb.sendLevel[efcCh]);
                    int revSampleR = (int) (sR[0] * effects.reverb.sendLevel[efcCh]);
                    dest[ptrDest + 0] += sL[0];
                    dest[ptrDest + 1] += sR[0];
                    effects.reverb.storeDataC(revSampleL, revSampleR);
                    //visAPCMVolume[0] = (int)(s & maskL);
                    //visAPCMVolume[1] = (int)(s & maskR);
                    ptrDest += 2;
                    adplC -= adplD;
                }
                for (; count > 0 && apOut0 != 0; count--) {
                    if (adplC < 0) {
                        apOut0 = apOut1;
                        apOut1 = 0;
                        adplC += 8192;
                    }
                    int s = (adplC * apOut1) >> 13;
                    int[] sL = new int[] {s & maskL};
                    int[] sR = new int[] {s & maskR};
                    effects.distortion.mix(efcCh, sL, sR);
                    effects.chorus.mix(efcCh, sL, sR);
                    effects.hpflpf.mix(efcCh, sL, sR);
                    effects.compressor.mix(efcCh, sL, sR);

                    sL[0] = (int) (sL[0] * panL) * ReversePhase.adpcm[num][0];
                    sR[0] = (int) (sR[0] * panR) * ReversePhase.adpcm[num][1];
                    int revSampleL = (int) (sL[0] * effects.reverb.sendLevel[efcCh]);
                    int revSampleR = (int) (sR[0] * effects.reverb.sendLevel[efcCh]);
                    dest[ptrDest + 0] += sL[0];
                    dest[ptrDest + 1] += sR[0];
                    effects.reverb.storeDataC(revSampleL, revSampleR);
                    //visAPCMVolume[0] = (int)(s & maskL);
                    //visAPCMVolume[1] = (int)(s & maskR);
                    ptrDest += 2;
                    adplC -= adplD;
                }
            } else { // fplay > fsamp (adpld = fplay/famp*8192)
                int t = (-8192 * 8192) / adplD;
stop:
                for (; count > 0; count--) {
                    int s = apOut0 * (8192 + adplC);
                    while (adplC < 0) {
                        decode();
                        if (!adpcmPlay)
                            break stop;
                        s -= apOut0 * Math.max(adplC, t);
                        adplC -= t;
                    }
                    adplC -= 8192;
                    s >>= 13;
                    int[] sL = new int[] {s & maskL};
                    int[] sR = new int[] {s & maskR};
                    effects.distortion.mix(efcCh, sL, sR);
                    effects.chorus.mix(efcCh, sL, sR);
                    effects.hpflpf.mix(efcCh, sL, sR);
                    effects.compressor.mix(efcCh, sL, sR);

                    sL[0] = (int) (sL[0] * panL) * ReversePhase.adpcm[num][0];
                    sR[0] = (int) (sR[0] * panR) * ReversePhase.adpcm[num][1];
                    int revSampleL = (int) (sL[0] * effects.reverb.sendLevel[efcCh]);
                    int revSampleR = (int) (sR[0] * effects.reverb.sendLevel[efcCh]);
                    dest[ptrDest + 0] += sL[0];
                    dest[ptrDest + 1] += sR[0];
                    effects.reverb.storeDataC(revSampleL, revSampleR);
                    ptrDest += 2;
                }
            }
        }
        if (!adpcmPlay) {
            apOut0 = apOut1 = adpcmOut = 0;
            adplC = 0;
        }
    }

    public void setReg(int addr, int data) {
        switch (addr) {
        case 0x00: // Controller Register 1
            if (((data & 0x80) != 0) && !adpcmPlay) {
                adpcmPlay = true;
                memAddr = startAddr;
                adpcMx = 0;
                adpcmD = 127;
                adplC = 0;
            }
            if ((data & 1) != 0) {
                adpcmPlay = false;
            }
            control1 = (byte) data;
            break;

        case 0x01: // Controller Register 2
            control2 = (byte) data;
            granuality = (byte) ((control2 & 2) != 0 ? 1 : 4);
            break;

        case 0x02: // Start Address L
        case 0x03: // Start Address H
            adpcmReg[addr - 0x02 + 0] = (byte) data;
            startAddr = (adpcmReg[1] * 256 + adpcmReg[0]) << shiftBit;
            memAddr = startAddr;
            // Debug.printf("  startaddr %.6x", startaddr);
            break;

        case 0x04: // Stop Address L
        case 0x05: // Stop Address H
            adpcmReg[addr - 0x04 + 2] = (byte) data;
            stopAddr = (adpcmReg[3] * 256 + adpcmReg[2] + 1) << shiftBit;
            // Debug.printf("  stopaddr %.6x", stopaddr);
            break;

        case 0x07:
            panL = OPNA2.panTable[(data >> 6) & 0x3];
            panR = OPNA2.panTable[(data >> 4) & 0x3];
            break;

        case 0x08: // ADPCM data
            if ((control1 & 0x60) == 0x60) {
                //   LOG2("  Wr [0x%.5x] = %.2x", memaddr, data);
                writeRam(data);
            }
            break;

        case 0x09: // delta-N L
        case 0x0a: // delta-N H
            adpcmReg[addr - 0x09 + 4] = (byte) data;
            deltaN = adpcmReg[5] * 256 + adpcmReg[4];
            deltaN = Math.max(256, deltaN);
            adplD = deltaN * adplBase >> 16;
            break;

        case 0x0b: // Level Controller
            adpcmLevel = (byte) data;
            adpcmVolume = (adpcmVol * adpcmLevel) >> 12;
            break;

        case 0x0c: // Limit Address L
        case 0x0d: // Limit Address H
            adpcmReg[addr - 0x0c + 6] = (byte) data;
            limitAddr = (adpcmReg[7] * 256 + adpcmReg[6] + 1) << shiftBit;
            // Debug.printf("  limitaddr %.6x", limitaddr);
            break;

        case 0x10: // Flag Controller
            if ((data & 0x80) != 0) {
                status = 0;
                updateStatus();
            } else {
                stMask = ~(data & 0x1f);
                // updateStatus(); // ???
            }
            break;
        }
    }

    /**
     * ADPCM RAM への書込み操作
     */
    protected void writeRam(int data) {
        if (NO_BITTYPE_EMULATION) {
            if ((control2 & 2) == 0) {
                // 1 bit mode
                adpcmBuf[(memAddr >> 4) & adpcmMask] = (byte) data;
                memAddr += 16;
            } else {
                // 8 bit mode
                int p = (memAddr >> 4) & 0x7fff;
                int bank = (memAddr >> 1) & 7;
                byte mask = (byte) (1 << bank);
                data <<= bank;

                adpcmBuf[p + 0x00000] = (byte) ((adpcmBuf[p + 0x00000] & ~mask) | ((byte) (data) & mask));
                data >>= 1;
                adpcmBuf[p + 0x08000] = (byte) ((adpcmBuf[p + 0x08000] & ~mask) | ((byte) (data) & mask));
                data >>= 1;
                adpcmBuf[p + 0x10000] = (byte) ((adpcmBuf[p + 0x10000] & ~mask) | ((byte) (data) & mask));
                data >>= 1;
                adpcmBuf[p + 0x18000] = (byte) ((adpcmBuf[p + 0x18000] & ~mask) | ((byte) (data) & mask));
                data >>= 1;
                adpcmBuf[p + 0x20000] = (byte) ((adpcmBuf[p + 0x20000] & ~mask) | ((byte) (data) & mask));
                data >>= 1;
                adpcmBuf[p + 0x28000] = (byte) ((adpcmBuf[p + 0x28000] & ~mask) | ((byte) (data) & mask));
                data >>= 1;
                adpcmBuf[p + 0x30000] = (byte) ((adpcmBuf[p + 0x30000] & ~mask) | ((byte) (data) & mask));
                data >>= 1;
                adpcmBuf[p + 0x38000] = (byte) ((adpcmBuf[p + 0x38000] & ~mask) | ((byte) (data) & mask));
                memAddr += 2;
            }
        } else {
            adpcmBuf[(memAddr >> granuality) & adpcmMask] = (byte) data;
            memAddr += 1 << granuality;
        }

        if (memAddr == stopAddr) {
            setStatus(4);
            statusNext = 0x04; // EOS
            memAddr &= (shiftBit == 6) ? 0x3fffff : 0x1ffffff;
        }
        if (memAddr == limitAddr) {
            // Debug.printf("Limit ! (%.8x)\n", limitaddr);
            memAddr = 0;
        }
        setStatus(8);
    }

    /**
     * ADPCM 展開
     */
    protected void decode() {
        apOut0 = apOut1;
        int n = (readRam() * adpcmVolume) >> 13;
        apOut1 = adpcmOut + n;
        adpcmOut = n;
    }

    /**
     * ADPCM RAM からの nibble 読み込み及び ADPCM 展開
     */
    protected int readRam() {
        int data;
        if (granuality > 0) {
            if (NO_BITTYPE_EMULATION) {
                if ((control2 & 2) == 0) {
                    data = adpcmBuf[(memAddr >> 4) & adpcmMask];
                    memAddr += 8;
                    if ((memAddr & 8) != 0)
                        return decodeSample(data >> 4);
                    data &= 0x0f;
                } else {
                    int p = ((memAddr >> 4) & 0x7fff) + ((~memAddr & 1) << 17);
                    int bank = (memAddr >> 1) & 7;
                    byte mask = (byte) (1 << bank);

                    data = adpcmBuf[p + 0x18000] & mask;
                    data = data * 2 + (adpcmBuf[p + 0x10000] & mask);
                    data = data * 2 + (adpcmBuf[p + 0x08000] & mask);
                    data = data * 2 + (adpcmBuf[p + 0x00000] & mask);
                    data >>= bank;
                    memAddr++;
                    if ((memAddr & 1) != 0)
                        return decodeSample(data);
                }
            } else {
                data = adpcmBuf[(memAddr >> granuality) & adpcmMask];
                memAddr += 1 << (granuality - 1);
                if ((memAddr & (1 << (granuality - 1))) != 0)
                    return decodeSample(data >> 4);
                data &= 0x0f;
            }
        } else {
            data = adpcmBuf[(memAddr >> 1) & adpcmMask];
            ++memAddr;
            if ((memAddr & 1) != 0)
                return decodeSample(data >> 4);
            data &= 0x0f;
        }

        decodeSample(data);

        // check
        if (memAddr == stopAddr) {
            if ((control1 & 0x10) != 0) {
                memAddr = startAddr;
                data = adpcMx;
                adpcMx = 0;
                adpcmD = 127;
                return data;
            } else {
                memAddr &= adpcmMask; // 0x3fffff;
                setStatus(adpcmNotice);
                adpcmPlay = false;
            }
        }

        if (memAddr == limitAddr)
            memAddr = 0;

        return adpcMx;
    }

    private static final int[] table1 = new int[] {
            1, 3, 5, 7, 9, 11, 13, 15,
            -1, -3, -5, -7, -9, -11, -13, -15,
    };

    private static final int[] table2 = new int[] {
            57, 57, 57, 57, 77, 102, 128, 153,
            57, 57, 57, 57, 77, 102, 128, 153,
    };

    protected int decodeSample(int data) {
        adpcMx = Fmvgen.limit(adpcMx + table1[data] * adpcmD / 8, 32767, -32768);
        adpcmD = Fmvgen.limit(adpcmD * table2[data] / 64, 24576, 127);
        return adpcMx;
    }

    /**
     * ステータスフラグ設定
     */
    protected void setStatus(int bits) {
        if ((status & bits) == 0) {
//Debug.printf("SetStatus(%.2x %.2x)\n", bits, stmask);
            status |= bits & stMask;
            updateStatus();
        }
//else
// Debug.printf("SetStatus(%.2x) - ignored\n", bits);
    }

    protected void resetStatus(int bits) {
        status &= ~bits;
        // Debug.printf("ResetStatus(%.2x)\n", bits);
        updateStatus();
    }

    protected void updateStatus() {
//Debug.printf("%d:INT = %d\n", Diag::GetCPUTick(), (status & stmask & reg29) != 0);
        //intr((status & stmask & reg29) != 0);
    }

    private void intr(boolean f) {
    }

    public void reset() {
       this.statusNext = 0;
       this.memAddr = 0;
       this.adpcmD = 127;
       this.adpcMx = 0;
       this.adpcmPlay = false;
       this.adplC = 0;
       this.adplD = 0x100;
    }
}
