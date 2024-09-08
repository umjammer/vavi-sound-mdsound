package mdsound;

import java.util.ArrayList;
import java.util.List;

import mdsound.instrument.Ay8910Inst;
import mdsound.instrument.DmgInst;
import mdsound.instrument.HuC6280Inst;
import mdsound.instrument.IntFNesInst;
import mdsound.instrument.MameYm2612Inst;
import mdsound.instrument.OkiM6258Inst;
import mdsound.instrument.PwmInst;
import mdsound.instrument.ScdPcmInst;
import mdsound.instrument.Sn76489Inst;
import mdsound.instrument.Sn76496Inst;
import mdsound.instrument.Y8950Inst;
import mdsound.instrument.Ym2151Inst;
import mdsound.instrument.Ym2203Inst;
import mdsound.instrument.Ym2413Inst;
import mdsound.instrument.Ym2608Inst;
import mdsound.instrument.Ym2610Inst;
import mdsound.instrument.Ym2612Inst;
import mdsound.instrument.Ym3438Inst;
import mdsound.instrument.Ym3526Inst;
import mdsound.instrument.Ym3812Inst;
import mdsound.instrument.YmF262Inst;
import mdsound.instrument.YmF271Inst;
import mdsound.instrument.YmF278bInst;
import mdsound.instrument.YmZ280bInst;
import vavi.util.ByteUtil;


public class DacControl {

    public static class PcmData {
        public int dataSize;
        public byte[] data;
        public int dataStart;
    }

    public static class PcmBank {
        public int bankCount;
        public List<PcmData> bank = new ArrayList<>();
        public int dataSize;
        public byte[] data;
        public int dataPos;
        public int bnkPos;
    }

    public static class ControlData {
        public boolean enable;
        public int bank;
    }

    public static class PcmBankTable {
        public int comprType;
        public int cmpSubType;
        public int bitDec;
        public int bitCmp;
        public int entryCount;
        public byte[] entries;
    }

    public static class Control {
        // Commands sent to dest-chips
        public int dstChipType;
        public int dstEmuType;
        public int dstChipIndex;
        public int dstchipId;
        public int dstCommand;
        public int cmdSize;

        /** Frequency (Hz) at which the commands are sent */
        public int frequency;
        /** to protect from reading beyond End Of data */
        public int dataLen;
        public byte[] data;
        /** Position where to start */
        public int dataStart;
        /** usually 1, set to 2 for L/R interleaved data */
        public int stepSize;
        /** usually 0, set to 0/1 for L/R interleaved data */
        public int stepBase;
        public int cmdsToSend;

        // Running Bits: 0 (01) - is playing
        //     2 (04) - loop sample (simple loop from start to end)
        //     4 (10) - already sent this command
        //     7 (80) - disabled
        public int running;
        public int reverse;
        /** Position in Player SampleRate */
        public int step;
        /** Position in data SampleRate */
        public int pos;
        public int remainCmds;
        /** true Position in data (== Pos, if Reverse is off) */
        public int realPos;
        /** always StepSize * CmdSize */
        public int dataStep;
    }

    private static final int DCTRL_LMODE_IGNORE = 0x00;
    private static final int DCTRL_LMODE_CMDS = 0x01;
    private static final int DCTRL_LMODE_MSEC = 0x02;
    private static final int DCTRL_LMODE_TOEND = 0x03;
    private static final int DCTRL_LMODE_BYTES = 0x0F;
    private static final int MAX_CHIPS = 0xff;
    private static final int DAC_SMPL_RATE = 44100;
    private static final int PCM_BANK_COUNT = 0x40;

    private Control[] dacData = new Control[MAX_CHIPS];
    private MDSound mds = null;
    private final Object lock = new Object();
    private int samplingRate;
    private double pcmStep;
    private double pcmExecDelta;
    private int dacCtrlUsed;
    private byte[] dacCtrlUsg = new byte[MAX_CHIPS];
    private ControlData[] dacCtrl = new ControlData[0xff];
    public PcmBank[] pcmBank = null;
    private PcmBankTable pcmTbl = new PcmBankTable();

    public DacControl(int samplingRate, MDSound mds) {
        init(samplingRate, mds, null);
    }

    public void init(int samplingRate, MDSound mds, PcmBank[] pcmBank) {
        this.mds = mds;
        this.samplingRate = samplingRate;
        pcmStep = samplingRate / (double) DAC_SMPL_RATE;
        pcmExecDelta = 0;
        this.pcmBank = pcmBank;
        refresh();
        dacCtrlUsed = 0x00;
        for (int curChip = 0x00; curChip < 0xff; curChip++) {
            dacCtrl[curChip] = new ControlData();
            dacCtrl[curChip].enable = false;
        }
    }

    public void update() {
        while ((int) pcmExecDelta <= 0) {
            for (int curChip = 0x00; curChip < dacCtrlUsed; curChip++) {
                update(dacCtrlUsg[curChip], 1);
            }
            pcmExecDelta += pcmStep;
        }
        pcmExecDelta -= 1.0;
    }

    public void setupStreamControl(int si, int chipType, int emuType, int chipIndex, int chipId, int port, int cmd) {
        if (si == 0xff) return;

        if (!dacCtrl[si].enable) {
            deviceStartDaccontrol(si);
            deviceResetDaccontrol(si);
            dacCtrl[si & 0xff].enable = true;
            dacCtrlUsg[dacCtrlUsed] = (byte) si;
            dacCtrlUsed++;
        }

        setupChip(si, chipType, emuType, chipIndex, chipId & 0x7F, port * 0x100 + cmd);
    }

    public void setStreamData(int si, int bank, int stepSize, int stepBase) {
        if (si == 0xff) return;

        dacCtrl[si].bank = bank;
        if (dacCtrl[si & 0xff].bank >= PCM_BANK_COUNT)
            dacCtrl[si & 0xff].bank = 0x00;

        PcmBank tempPCM = pcmBank[dacCtrl[si].bank];
        //Last95Max = tempPCM.BankCount;
        setData(si, tempPCM.data, tempPCM.dataSize, stepSize, stepBase);
    }

    public void setStreamFrequency(int si, int tempLng) {
        if (si == 0xff || !dacCtrl[si & 0xff].enable) return;
        set_frequency(si, tempLng);
    }

    public void startStream(int si, int dataStart, int tempByt, int dataLen) {
        if (si == 0xff || !dacCtrl[si].enable || pcmBank[dacCtrl[si].bank].bankCount == 0)
            return;

        start(si, dataStart, tempByt, dataLen);
    }

    public void stopStream(int si) {
        if (!dacCtrl[si].enable)
            return;

        if ((si) < 0xff)
            stop(si);
        else
            for (int i = 0x00; i < 0xff; i++) stop(i);
    }

    public void startStreamFastCall(int curChip, int tempSht, int mode) {
        if (curChip == 0xff || !dacCtrl[curChip].enable ||
                pcmBank[dacCtrl[curChip].bank].bankCount == 0) {
            return;
        }

        PcmBank tempPCM = pcmBank[dacCtrl[curChip].bank];
        if (tempSht >= tempPCM.bankCount)
            tempSht = 0x00;

        PcmData tempBnk = tempPCM.bank.get(tempSht);

        int tempByt = DacControl.DCTRL_LMODE_BYTES |
                (mode & 0x10) |         // Reverse Mode
                ((mode & 0x01) << 7); // Looping
        start(curChip, tempBnk.dataStart, tempByt, tempBnk.dataSize);
    }

    public void addPCMData(byte type, int dataSize, int adr, byte[] vgmBuf) {
        int curBnk;
        PcmBank tempPCM;
        PcmData tempBnk;
        int bankSize;
        //boolean retVal;
        int bnkType;
        int curDAC;

        bnkType = type & 0x3F;
        if ((bnkType & 0xff) >= PCM_BANK_COUNT)
            return;

        if (type == 0x7F) {
            readPCMTable(vgmBuf, dataSize, adr);
            return;
        }

        tempPCM = pcmBank[bnkType];
        tempPCM.bnkPos++;
        if (tempPCM.bnkPos <= tempPCM.bankCount)
            return; // Speed hack for restarting playback (skip already loaded blocks)
        curBnk = tempPCM.bankCount;
        tempPCM.bankCount++;
        tempPCM.bank.add(new PcmData());

        if ((type & 0x40) == 0)
            bankSize = dataSize;
        else
            bankSize = ByteUtil.readLeInt(vgmBuf, adr + 1);

        byte[] newData = new byte[tempPCM.dataSize + bankSize];
        if (tempPCM.data != null && tempPCM.data.length > 0)
            System.arraycopy(tempPCM.data, 0, newData, 0, tempPCM.data.length);
        tempPCM.data = newData;

        tempBnk = tempPCM.bank.get(curBnk);
        tempBnk.dataStart = tempPCM.dataSize;
        tempBnk.data = new byte[bankSize];
        boolean retVal = true;
        if ((type & 0x40) == 0) {
            tempBnk.dataSize = dataSize;
            for (int i = 0; i < dataSize; i++) {
                tempPCM.data[i + tempBnk.dataStart] = vgmBuf[adr + i];
                tempBnk.data[i] = vgmBuf[adr + i];
            }
        } else {
            retVal = decompressDataBlk(vgmBuf, tempBnk, dataSize, adr);
            if (!retVal) {
                tempBnk.data = null;
                tempBnk.dataSize = 0x00;
            } else {
                System.arraycopy(tempBnk.data, 0, tempPCM.data, 0 + tempBnk.dataStart, bankSize);
            }
        }
        //if (bankSize != tempBnk.dataSize) Debug.printf("Error reading data Block! data Size conflict!\n");
        if (retVal)
            tempPCM.dataSize += bankSize;

        // realloc may've moved the Bank block, so refresh all DAC Streams
        for (curDAC = 0x00; curDAC < dacCtrlUsed; curDAC++) {
            if (dacCtrl[dacCtrlUsg[curDAC]].bank == bnkType)
                refresh_data(dacCtrlUsg[curDAC], tempPCM.data, tempPCM.dataSize);
        }
    }

    public byte getDACFromPCMBank() {
        // for Ym2612Inst DAC data only
        int dataPos = pcmBank[0x00].dataPos;
        if (dataPos >= pcmBank[0x00].dataSize)
            return (byte) 0x80;

        pcmBank[0x00].dataPos++;
        return pcmBank[0x00].bank.get(0).data[dataPos];
    }

    public Integer getPCMAddressFromPCMBank(int type, int dataPos) {
        if (type >= PCM_BANK_COUNT)
            return null;

        if (dataPos >= pcmBank[type].dataSize)
            return null;

        return dataPos;
    }

    public void refresh() {
        synchronized (lock) {
            for (int i = 0; i < MAX_CHIPS; i++) dacData[i] = new Control();
        }
    }

    private boolean decompressDataBlk(byte[] vgmBuf, PcmData bank, int dataSize, int adr) {
        int comprType;
        int bitDec;
        int bitCmp;
        int cmpSubType;
        int addVal;
        int inPos;
        int inDataEnd;
        int outPos;
        int outDataEnd;
        int inVal;
        int outVal = 0; // Fint outVal;
        int valSize;
        int inShift;
        int outShift;
        int ent1B = 0; // (int)8* ent1B;
        int ent2B = 0; // int* ent2B;
//#if defined(_DEBUG) && defined(WIN32)
        // (int)32 Time;
//#endif

        // ReadBits Variables
        int bitsToRead;
        int bitReadVal;
        int inValB;
        int bitMask;
        int outBit;

        // Variables for DPCM
        int outMask;

        comprType = vgmBuf[adr + 0] & 0xff;
        bank.dataSize = ByteUtil.readLeInt(vgmBuf, adr + 1);

        switch (comprType) {
        case 0x00: // n-Bit compression
            bitDec = vgmBuf[adr + 5] & 0xff;
            bitCmp = vgmBuf[adr + 6] & 0xff;
            cmpSubType = vgmBuf[adr + 7] & 0xff;
            addVal = ByteUtil.readLeShort(vgmBuf, adr + 8) & 0xffff;

            if (cmpSubType == 0x02) {
                //bank.dataSize = 0x00;
                //return false;

                ent1B = 0; // Big Endian note: Those are stored in LE and converted when reading.
                ent2B = 0; //
                if (pcmTbl.entryCount == 0) {
                    bank.dataSize = 0x00;
                    //Debug.printf("Error loading table-compressed data block! No table loaded!\n");
                    return false;
                } else if (bitDec != pcmTbl.bitDec || bitCmp != pcmTbl.bitCmp) {
                    bank.dataSize = 0x00;
                    //Debug.printf("Warning! data block and loaded value table incompatible!\n");
                    return false;
                }
            }

            valSize = (bitDec + 7) / 8;
            inPos = adr + 0x0A;
            inDataEnd = adr + dataSize;
            inShift = 0;
            outShift = bitDec - bitCmp;
            outDataEnd = bank.dataSize;

            for (outPos = 0; outPos < outDataEnd && inPos < inDataEnd; outPos += valSize) {
                // inlined - is 30% faster
                outBit = 0x00;
                inVal = 0x0000;
                bitsToRead = bitCmp;
                while (bitsToRead != 0) {
                    bitReadVal = Math.min(bitsToRead, 8);
                    bitsToRead -= bitReadVal;
                    bitMask = (1 << bitReadVal) - 1;

                    inShift += bitReadVal;
                    inValB = (vgmBuf[inPos] << inShift >> 8) & bitMask;
                    if (inShift >= 8) {
                        inShift -= 8;
                        inPos++;
                        if (inShift != 0)
                            inValB |= (vgmBuf[inPos] << inShift >> 8) & bitMask;
                    }

                    inVal |= inValB << outBit;
                    outBit += bitReadVal;
                }

                switch (cmpSubType) {
                case 0x00: // Copy
                    outVal = inVal + addVal;
                    break;
                case 0x01: // Shift Left
                    outVal = (inVal << outShift) + addVal;
                    break;
                case 0x02: // Table
                    switch (valSize) {
                    case 0x01:
                        outVal = pcmTbl.entries[ent1B + inVal] & 0xff;
                        break;
                    case 0x02:
//#ifndef BIG_ENDIAN
//                        outVal = ent2B[inVal];
//#else
                        //ReadLE16(((int)8*)&ent2B[inVal]);
                        outVal = (pcmTbl.entries[ent2B + inVal * 2] & 0xff) + (pcmTbl.entries[ent2B + inVal * 2 + 1] & 0xff) * 0x100;
//#endif
                        break;
                    }
                    break;
                }

//#ifndef BIG_ENDIAN
//               //memcpy(outPos, &outVal, valSize);
//               if (valSize == 0x01)
//                    *(((int)8*)outPos) = ((int)8)outVal;
//               else //if (valSize == 0x02)
//                    *((int*)outPos) = (int)outVal;
//#else
                if (valSize == 0x01) {
                    bank.data[outPos] = (byte) outVal;
                } else { //if (valSize == 0x02)
                    bank.data[outPos + 0x00] = (byte) ((outVal & 0x00FF) >> 0);
                    bank.data[outPos + 0x01] = (byte) ((outVal & 0xff00) >> 8);
                }
//#endif
            }
            break;
        case 0x01: // Delta-PCM
            bitDec = vgmBuf[adr + 5] & 0xff;
            bitCmp = vgmBuf[adr + 6] & 0xff;
            outVal = ByteUtil.readLeShort(vgmBuf, adr + 8) & 0xffff;

            ent1B = 0;
            ent2B = 0;
            if (pcmTbl.entryCount == 0) {
                bank.dataSize = 0x00;
                //Debug.printf("Error loading table-compressed data block! No table loaded!\n");
                return false;
            } else if (bitDec != pcmTbl.bitDec || bitCmp != pcmTbl.bitCmp) {
                bank.dataSize = 0x00;
                //Debug.printf("Warning! data block and loaded value table incompatible!\n");
                return false;
            }

            valSize = (bitDec + 7) / 8;
            outMask = (1 << bitDec) - 1;
            inPos = adr + 0xa;
            inDataEnd = adr + dataSize;
            inShift = 0;
            outShift = bitDec - bitCmp;
            outDataEnd = bank.dataSize;// bank.Data + bank.dataSize;
            addVal = 0x0000;

            for (outPos = 0; outPos < outDataEnd && inPos < inDataEnd; outPos += valSize) {
                // inlined - is 30% faster
                outBit = 0x00;
                inVal = 0x0000;
                bitsToRead = bitCmp;
                while (bitsToRead != 0) {
                    bitReadVal = Math.min(bitsToRead, 8);
                    bitsToRead -= bitReadVal;
                    bitMask = (1 << bitReadVal) - 1;

                    inShift += bitReadVal;
                    inValB = (vgmBuf[inPos] << inShift >> 8) & bitMask;
                    if (inShift >= 8) {
                        inShift -= 8;
                        inPos++;
                        if (inShift != 0)
                            inValB |= (vgmBuf[inPos] << inShift >> 8) & bitMask;
                    }

                    inVal |= inValB << outBit;
                    outBit += bitReadVal;
                }

                switch (valSize) {
                case 0x01:
                    addVal = pcmTbl.entries[ent1B + inVal];
                    outVal += addVal;
                    outVal &= outMask;
                    bank.data[outPos] = (byte) outVal;
                    break;
                case 0x02:
//#ifndef BIG_ENDIAN
                    //    addVal = ent2B[inVal];
//#else
                    addVal = (pcmTbl.entries[ent2B + inVal] & 0xff) + (pcmTbl.entries[ent2B + inVal + 1] & 0xff) * 0x100;
//#endif
                    outVal += addVal;
                    outVal &= outMask;
//#ifndef BIG_ENDIAN
                    //    *((int*)outPos) = (int)outVal;
//#else
                    bank.data[outPos + 0x00] = (byte) ((outVal & 0x00FF) >> 0);
                    bank.data[outPos + 0x01] = (byte) ((outVal & 0xff00) >> 8);
//#endif
                    break;
                }
            }
            break;
        default:
            //Debug.printf("Error: Unknown data block compression!\n");
            return false;
        }

        return true;
    }

    private void readPCMTable(byte[] vgmBuf, int dataSize, int adr) {
        int valSize;
        int tblSize;

        pcmTbl.comprType = vgmBuf[adr + 0] & 0xff;
        pcmTbl.cmpSubType = vgmBuf[adr + 1] & 0xff;
        pcmTbl.bitDec = vgmBuf[adr + 2] & 0xff;
        pcmTbl.bitCmp = vgmBuf[adr + 3] & 0xff;
        pcmTbl.entryCount = ByteUtil.readLeShort(vgmBuf, adr + 4) & 0xffff;

        valSize = (pcmTbl.bitDec + 7) / 8;
        tblSize = pcmTbl.entryCount * valSize;

        pcmTbl.entries = new byte[tblSize];
        for (int i = 0; i < tblSize; i++) pcmTbl.entries[i] = vgmBuf[adr + 6 + i];

//        if (dataSize < 0x06 + tblSize) {
//            //Debug.printf("Warning! Bad PCM Table Length!\n");
//        }
    }

    private void sendCommand(Control chip) {
        //注意!! chipはlock中です

        int port;
        int command;
        int data;

        if ((chip.running & 0x10) != 0) // command already sent
            return;
        if (chip.dataStart + chip.realPos >= chip.dataLen)
            return;

        //if (! chips.Reverse)
        //ChipData00 = chips.data[(chips.dataStart + chips.RealPos)];
        //ChipData01 = chips.data[(chips.dataStart + chips.RealPos+1)];
        //else
        // ChipData = chips.data + (chips.dataStart + chips.CmdsToSend - 1 - chips.Pos);
        switch (chip.dstChipType) {
        // Support for the important chips
        case 0x02: // Ym2612Inst (16-bit Register (actually 9 Bit), 8-bit data)
            port = (chip.dstCommand & 0xff00) >> 8;
            command = chip.dstCommand & 0x00FF;
            data = chip.data[chip.dataStart + chip.realPos] & 0xff;

            chipRegWrite(chip.dstChipType
                    , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                    , port, command, data);
            break;
        case 0x11: // PWM (4-bit Register, 12-bit data)
            port = chip.dstCommand & 0x000F;
            command = chip.data[chip.dataStart + chip.realPos + 1] & 0x0F;
            data = chip.data[chip.dataStart + chip.realPos] & 0xff;

            chipRegWrite(chip.dstChipType
                    , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                    , port, command, data);
            break;
        // Support for other chips (mainly for completeness)
        case 0x00: // Sn76496Inst (4-bit Register, 4-bit/10-bit data)
            command = chip.dstCommand & 0x00F0;
            data = chip.data[chip.dataStart + chip.realPos] & 0x0F;

            if ((command & 0x10) != 0) {
                // Volume Change (4-Bit value)
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , 0x00, 0x00, command | data);
            } else {
                // Frequency Write (10-Bit value)
                port = ((chip.data[chip.dataStart + chip.realPos + 1] & 0x03) << 4) | ((chip.data[chip.dataStart + chip.realPos] & 0xF0) >> 4);
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , 0x00, 0x00, command | data);
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , 0x00, 0x00, port);
            }
            break;
        case 0x18: // OKIM6295 - TODO: verify
            command = chip.dstCommand & 0x00FF;
            data = chip.data[chip.dataStart + chip.realPos] & 0xff;

            if (command == 0) {
                port = (byte) ((chip.dstCommand & 0x0F00) >> 8);
                if ((data & 0x80) > 0) {
                    // Sample Start
                    // write sample ID
                    chipRegWrite(chip.dstChipType
                            , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                            , 0x00, command, data);
                    // write channel(s) that should play the sample
                    chipRegWrite(chip.dstChipType
                            , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                            , 0x00, command, port << 4);
                } else {
                    // Sample Stop
                    chipRegWrite(chip.dstChipType
                            , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                            , 0x00, command, port << 3);
                }
            } else {
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , 0x00, command, data);
            }
            break;
        // Generic support: 8-bit Register, 8-bit data
        case 0x01: // YM2413
        case 0x03: // YM2151
        case 0x06: // YM2203
        case 0x09: // YM3812
        case 0x0A: // YM3526
        case 0x0B: // Y8950Inst
        case 0x0F: // YMZ280B
        case 0x12: // AY8910
        case 0x13: // GameBoy DMG
        case 0x14: // NES APU
            // case 0x15: // MultiPCM
        case 0x16: // UPD7759
        case 0x17: // OKIM6258
        case 0x1D: // K053260Inst - TODO: Verify
        case 0x1E: // PokeyInst - TODO: Verify
            command = chip.dstCommand & 0x00FF;
            data = chip.data[chip.dataStart + chip.realPos] & 0xff;
            chipRegWrite(chip.dstChipType
                    , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                    , 0x00, command, data);
            break;
        // Generic support: 16-bit Register, 8-bit data
        case 0x07: // YM2608
        case 0x08: // YM2610/B
        case 0x0C: // YMF262
        case 0x0D: // YMF278B
        case 0x0E: // YMF271
        case 0x19: // K051649Inst - TODO: Verify
        case 0x1A: // K054539Inst - TODO: Verify
        case 0x1C: // C140Inst - TODO: Verify
            port = (chip.dstCommand & 0xff00) >> 8;
            command = chip.dstCommand & 0x00FF;
            data = chip.data[chip.dataStart + chip.realPos] & 0xff;
            chipRegWrite(chip.dstChipType
                    , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                    , port, command, data);
            break;
        // Generic support: 8-bit Register with Channel Select, 8-bit data
        case 0x05: // RF5C68
        case 0x10: // RF5C164
        case 0x1B: // OotakeHuC6280
            port = (chip.dstCommand & 0xff00) >> 8;
            command = chip.dstCommand & 0x00FF;
            data = chip.data[chip.dataStart + chip.realPos] & 0xff;

            if (port == 0xff) // Send Channel Select
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , 0x00, command & 0x0f, data);
            else {
                int prevChn;

                prevChn = port; // by default don't restore channel
                // get current channel for supported chips
                if (chip.dstChipType == 0x05) {
                }   // TODO
                else if (chip.dstChipType == 0x05) {
                }   // TODO
                else if (chip.dstChipType == 0x1B)
                    prevChn = mds.readOotakePsg(chip.dstChipIndex, chip.dstchipId, 0x00);

                // Send Channel Select
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , 0x00, command >> 4, port);
                // Send data
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , 0x00, command & 0x0F, data);
                // restore old channel
                if (prevChn != port)
                    chipRegWrite(chip.dstChipType
                            , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                            , 0x00, command >> 4, prevChn);

            }
            break;
        // Generic support: 8-bit Register, 16-bit data
        case 0x1F: // QSoundInst
            command = chip.dstCommand & 0x00FF;
            chipRegWrite(chip.dstChipType
                    , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                    , chip.data[chip.dataStart + chip.realPos] & 0xff, chip.data[chip.dataStart + chip.realPos + 1] & 0xff, command);
            break;
        }
        chip.running |= 0x10;
    }

    private int muldiv64round(int multiplicand, int multiplier, int divisor) {
        // Yes, I'm correctly rounding the values.
        return (multiplicand * multiplier + divisor / 2) / divisor;
    }

    private void update(int chipId, int samples) {
        synchronized (lock) {
            Control chip = dacData[chipId];
            int newPos;
            int realDataStp;

            //Debug.printf("DAC update chipId%d samples%d chips.Running%d ", chipId, samples, chips.Running);
            if ((chip.running & 0x80) != 0) // disabled
                return;
            if ((chip.running & 0x01) == 0) // stopped
                return;

            if (chip.reverse == 0)
                realDataStp = chip.dataStep;
            else
                realDataStp = -chip.dataStep;

            if (samples > 0x20) {
                // very effective Speed Hack for fast seeking
                newPos = chip.step + (samples - 0x10);
                newPos = muldiv64round(newPos * chip.dataStep, chip.frequency, DAC_SMPL_RATE);
                while (chip.remainCmds != 0 && chip.pos < newPos) {
                    chip.pos += chip.dataStep;
                    chip.realPos = chip.realPos + realDataStp;
                    chip.remainCmds--;
                }
            }

            chip.step += samples;
            // Formula: Step * Freq / SampleRate
            newPos = muldiv64round(chip.step * chip.dataStep, chip.frequency, DAC_SMPL_RATE);
            //Debug.printf("newPos%d chips.Step%d chips.DataStep%d chips.Frequency%d DAC_SMPL_RATE%d \n", newPos, chips.Step, chips.DataStep, chips.Frequency, (int)common.SampleRate);
            sendCommand(chip);

            while (chip.remainCmds != 0 && chip.pos < newPos) {
                sendCommand(chip);
                chip.pos += chip.dataStep;
                //if(model== enmModel.RealModel)                log.Write(String.format("datastep:%d",chips.DataStep));
                chip.realPos = chip.realPos + realDataStp;
                chip.running &= 0xef;// ~0x10;
                chip.remainCmds--;
            }

            if (chip.remainCmds == 0 && ((chip.running & 0x04) != 0)) {
                // loop back to start
                chip.remainCmds = chip.cmdsToSend;
                chip.step = 0x00;
                chip.pos = 0x00;
                if (chip.reverse == 0)
                    chip.realPos = 0x00;
                else
                    chip.realPos = (chip.cmdsToSend - 0x01) * chip.dataStep;
            }

            if (chip.remainCmds == 0)
                chip.running &= 0xfe;// ~0x01; // stop
        }
    }

    private byte deviceStartDaccontrol(int chipId) {
        Control chip;

        if (chipId >= MAX_CHIPS)
            return 0;

        synchronized (lock) {
            chip = dacData[chipId];

            chip.dstChipType = (byte) 0xff;
            chip.dstchipId = 0x00;
            chip.dstCommand = 0x0000;

            chip.running = (byte) 0xff; // disable all actions (except setup_chip)
        }

        return 1;
    }

    public void deviceStopDaccontrol(int chipId) {
        synchronized (lock) {
            Control chip = dacData[chipId];
            chip.running = (byte) 0xff;
        }
    }

    private void deviceResetDaccontrol(int chipId) {
        synchronized (lock) {
            Control chip = dacData[chipId];

            chip.dstChipType = 0x00;
            chip.dstchipId = 0x00;
            chip.dstCommand = 0x00;
            chip.cmdSize = 0x00;

            chip.frequency = 0;
            chip.dataLen = 0x00;
            chip.data = null;
            chip.dataStart = 0x00;
            chip.stepSize = 0x00;
            chip.stepBase = 0x00;

            chip.running = 0x00;
            chip.reverse = 0x00;
            chip.step = 0x00;
            chip.pos = 0x00;
            chip.realPos = 0x00;
            chip.remainCmds = 0x00;
            chip.dataStep = 0x00;
        }
    }

    private void setupChip(int chipId, int chType, int emuType, int chipIndex, int chNum, int command) {
        synchronized (lock) {
            Control chip = dacData[chipId];

            chip.dstChipType = chType; // TypeID (e.g. 0x02 for Ym2612Inst)
            chip.dstEmuType = emuType;
            chip.dstChipIndex = chipIndex;
            chip.dstchipId = chNum; // chips number (to send commands to 1st or 2nd chips)
            chip.dstCommand = command; // Port and command (would be 0x02A for Ym2612Inst)

            switch (chip.dstChipType) {
            case 0x00: // Sn76496Inst
                if ((chip.dstCommand & 0x0010) > 0)
                    chip.cmdSize = 0x01; // Volume Write
                else
                    chip.cmdSize = 0x02; // Frequency Write
                break;
            case 0x02: // Ym2612Inst
                chip.cmdSize = 0x01;
                break;
            case 0x11: // PWM
            case 0x1F: // QSoundInst
                chip.cmdSize = 0x02;
                break;
            default:
                chip.cmdSize = 0x01;
                break;
            }
            chip.dataStep = chip.cmdSize * chip.stepSize;
        }
    }

    private void setData(int chipId, byte[] data, int dataLen, int stepSize, int stepBase) {
        synchronized (lock) {
            Control chip = dacData[chipId];

            if ((chip.running & 0x80) > 0)
                return;

            if (dataLen > 0 && data != null) {
                chip.dataLen = dataLen;
                chip.data = data;
            } else {
                chip.dataLen = 0x00;
                chip.data = null;
            }
            chip.stepSize = stepSize > 0 ? stepSize : 1;
            chip.stepBase = stepBase;
            chip.dataStep = chip.cmdSize * chip.stepSize;
        }
    }

    private void refresh_data(int chipId, byte[] data, int dataLen) {
        synchronized (lock) {
            // Should be called to fix the data pointer. (e.g. after a realloc)
            Control chip = dacData[chipId];

            if ((chip.running & 0x80) != 0)
                return;

            if (dataLen > 0 && data != null) {
                chip.dataLen = dataLen;
                chip.data = data;
            } else {
                chip.dataLen = 0x00;
                chip.data = null;
            }
        }
    }

    private void set_frequency(int chipId, int frequency) {
        synchronized (lock) {
            //Debug.printf("chipId%d frequency%d", chipId, frequency);
            Control chip = dacData[chipId];

            if ((chip.running & 0x80) != 0)
                return;

            if (frequency != 0)
                chip.step = chip.step * chip.frequency / frequency;
            chip.frequency = frequency;
        }
    }

    private void start(int chipId, int dataPos, int lenMode, int length) {
        synchronized (lock) {
            Control chip = dacData[chipId];

            int cmdStepBase;

            if ((chip.running & 0x80) != 0)
                return;

            cmdStepBase = chip.cmdSize * chip.stepBase;
            if (dataPos != 0xffff_ffff) { // skip setting dataStart, if Pos == -1
                chip.dataStart = dataPos + cmdStepBase;
                if (chip.dataStart > chip.dataLen) // catch bad value and force silence
                    chip.dataStart = chip.dataLen;
            }

            switch (lenMode & 0x0F) {
            case DCTRL_LMODE_IGNORE: // length is already set - ignore
                break;
            case DCTRL_LMODE_CMDS: // length = number of commands
                chip.cmdsToSend = length;
                break;
            case DCTRL_LMODE_MSEC: // length = time in msec
                chip.cmdsToSend = 1000 * length / chip.frequency;
                break;
            case DCTRL_LMODE_TOEND: // play unti stop-command is received (or data-end is reached)
                chip.cmdsToSend = (chip.dataLen - (chip.dataStart - cmdStepBase)) / chip.dataStep;
                break;
            case DCTRL_LMODE_BYTES: // raw byte count
                chip.cmdsToSend = length / chip.dataStep;
                break;
            default:
                chip.cmdsToSend = 0x00;
                break;
            }
            chip.reverse = (lenMode & 0x10) >> 4;

            chip.remainCmds = chip.cmdsToSend;
            chip.step = 0x00;
            chip.pos = 0x00;
            if (chip.reverse == 0)
                chip.realPos = 0x00;
            else
                chip.realPos = (chip.cmdsToSend - 0x01) * chip.dataStep;

            chip.running &= 0xfb; // ~0x04;
            chip.running |= (lenMode & 0x80) != 0 ? 0x04 : 0x00; // set loop mode

            chip.running |= 0x01; // start
            chip.running &= 0xef; // ~0x10; // command isn't yet sent
        }
    }

    private void stop(int chipId) {
        synchronized (lock) {
            Control chip = dacData[chipId];

            if ((chip.running & 0x80) != 0)
                return;

            chip.running &= 0xfe; // ~0x01; // stop
        }
    }

    private void chipRegWrite(int chipType, int emuType, int chipIndex, int chipId, int port, int offset, int data) {
        switch (chipType) {
        case 0x00: // SN76489
            if (emuType == 0) mds.write(Sn76489Inst.class, chipIndex, 0, chipId, data);
            else if (emuType == 1) mds.write(Sn76496Inst.class, chipIndex, 0, chipId, data);
            break;
        case 0x01: // YM2413+
            mds.write(Ym2413Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x02: // Ym2612Inst
            if (emuType == 0) mds.write(Ym2612Inst.class, chipIndex, chipId, port, offset, data);
            else if (emuType == 1) mds.write(Ym3438Inst.class, chipIndex, chipId, port, offset, data);
            else if (emuType == 2) mds.write(MameYm2612Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x03: // YM2151+
            mds.write(Ym2151Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x06: // YM2203+
            mds.write(Ym2203Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x07: // YM2608+
            mds.write(Ym2608Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x08: // YM2610+
            mds.write(Ym2610Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x09: // YM3812+
            mds.write(Ym3812Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x0A: // YM3526+
            mds.write(Ym3526Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x0B: // Y8950Inst+
            mds.write(Y8950Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x0C: // YMF262+
            mds.write(YmF262Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x0D: // YMF278B+
            mds.write(YmF278bInst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x0E: // YMF271+
            mds.write(YmF271Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x0F: // YMZ280B+
            mds.write(YmZ280bInst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x10: // Rf5c164
            mds.write(ScdPcmInst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x11: // PWM
            mds.write(PwmInst.class, chipIndex, chipId, port, (offset << 8) | data);
            break;
        case 0x12: // AY8910+
            mds.write(Ay8910Inst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x13: // DMG+
            mds.write(DmgInst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x14: // NES+
            mds.write(IntFNesInst.class, chipIndex, chipId, port, offset, data);
            break;
        case 0x17: // OKIM6258
            //Debug.printf("[DAC]");
            mds.write(OkiM6258Inst.class, chipIndex, chipId, offset, data);
            break;
        case 0x1b: // OotakeHuC6280
            mds.write(HuC6280Inst.class, chipIndex, chipId, offset, data);
            break;
        }
    }
}
