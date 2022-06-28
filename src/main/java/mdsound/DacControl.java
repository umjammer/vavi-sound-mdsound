package mdsound;

import java.util.ArrayList;
import java.util.List;


public class DacControl {

    public static class VGM_PCM_DATA {
        public int dataSize;
        public byte[] data;
        public int dataStart;
    }

    public static class VGM_PCM_BANK {
        public int bankCount;
        public List<VGM_PCM_DATA> bank = new ArrayList<>();
        public int dataSize;
        public byte[] data;
        public int dataPos;
        public int bnkPos;
    }

    public static class DACCTRL_DATA {
        public boolean enable;
        public byte bank;
    }

    public static class PCMBANK_TBL {
        public byte comprType;
        public byte cmpSubType;
        public byte bitDec;
        public byte bitCmp;
        public int entryCount;
        public byte[] entries;
    }

    public static class DacControl_ {
        // Commands sent to dest-chip
        public byte dstChipType;
        public byte dstEmuType;
        public byte dstChipIndex;
        public byte dstchipId;
        public int dstCommand;
        public byte cmdSize;

        public int frequency; // Frequency (Hz) at which the commands are sent
        public int dataLen; // to protect from reading beyond End Of Data
        public byte[] data;
        public int dataStart; // Position where to start
        public byte stepSize; // usually 1, set to 2 for L/R interleaved data
        public byte stepBase; // usually 0, set to 0/1 for L/R interleaved data
        public int cmdsToSend;

        // Running Bits: 0 (01) - is playing
        //     2 (04) - loop sample (simple loop from start to end)
        //     4 (10) - already sent this command
        //     7 (80) - disabled
        public byte running;
        public byte reverse;
        public int step; // Position in Player SampleRate
        public int pos; // Position in Data SampleRate
        public int remainCmds;
        public int realPos; // true Position in Data (== Pos, if Reverse is off)
        public byte dataStep; // always StepSize * CmdSize
    }

    private static final byte DCTRL_LMODE_IGNORE = 0x00;
    private static final byte DCTRL_LMODE_CMDS = 0x01;
    private static final byte DCTRL_LMODE_MSEC = 0x02;
    private static final byte DCTRL_LMODE_TOEND = 0x03;
    private static final byte DCTRL_LMODE_BYTES = 0x0F;
    private static final int MAX_CHIPS = 0xFF;
    private static final int DAC_SMPL_RATE = 44100;
    private static final int PCM_BANK_COUNT = 0x40;

    private DacControl_[] dacData = new DacControl_[MAX_CHIPS];
    private MDSound mds = null;
    private final Object lockObj = new Object();
    private int samplingRate;
    private double pcmStep;
    private double pcmExecDelta;
    private byte dacCtrlUsed;
    private byte[] dacCtrlUsg = new byte[MAX_CHIPS];
    private DACCTRL_DATA[] dacCtrl = new DACCTRL_DATA[0xFF];
    public VGM_PCM_BANK[] pcmBank = null;
    private PCMBANK_TBL pcmTbl = new PCMBANK_TBL();

    public DacControl(int samplingRate, MDSound mds) {
        init(samplingRate, mds, null);
    }

    public void init(int samplingRate, MDSound mds, VGM_PCM_BANK[] pcmBank) {
        this.mds = mds;
        this.samplingRate = samplingRate;
        pcmStep = samplingRate / (double) DAC_SMPL_RATE;
        pcmExecDelta = 0;
        this.pcmBank = pcmBank;
        refresh();
        dacCtrlUsed = 0x00;
        for (byte curChip = 0x00; curChip < 0xFF; curChip++) {
            dacCtrl[curChip] = new DACCTRL_DATA();
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

    public void setupStreamControl(byte si, byte chipType, byte emuType, byte chipIndex, byte chipId, byte port, byte cmd) {
        if (si == 0xFF) return;

        if (!dacCtrl[si].enable) {
            deviceStartDaccontrol(si);
            deviceResetDaccontrol(si);
            dacCtrl[si].enable = true;
            dacCtrlUsg[dacCtrlUsed] = si;
            dacCtrlUsed++;
        }

        setupChip(si, chipType, emuType, chipIndex, (byte) (chipId & 0x7F), port * 0x100 + cmd);
    }

    public void setStreamData(byte si, byte bank, byte stepSize, byte stepBase) {
        if (si == 0xFF) return;

        dacCtrl[si].bank = bank;
        if (dacCtrl[si].bank >= PCM_BANK_COUNT)
            dacCtrl[si].bank = 0x00;

        VGM_PCM_BANK tempPCM = pcmBank[dacCtrl[si].bank];
        //Last95Max = tempPCM.BankCount;
        setData(si, tempPCM.data, tempPCM.dataSize, stepSize, stepBase);
    }

    public void setStreamFrequency(byte si, int tempLng) {
        if (si == 0xFF || !dacCtrl[si].enable) return;
        set_frequency(si, tempLng);
    }

    public void startStream(byte si, int dataStart, byte tempByt, int dataLen) {
        if (si == 0xFF || !dacCtrl[si].enable || pcmBank[dacCtrl[si].bank].bankCount == 0)
            return;

        start(si, dataStart, tempByt, dataLen);
    }

    public void stopStream(byte si) {
        if (!dacCtrl[si].enable)
            return;

        if (si < 0xFF)
            stop(si);
        else
            for (si = 0x00; si < 0xFF; si++) stop(si);
    }

    public void startStreamFastCall(byte curChip, int tempSht, byte mode) {
        if (curChip == 0xFF || !dacCtrl[curChip].enable ||
                pcmBank[dacCtrl[curChip].bank].bankCount == 0) {
            return;
        }

        VGM_PCM_BANK tempPCM = pcmBank[dacCtrl[curChip].bank];
        if (tempSht >= tempPCM.bankCount)
            tempSht = 0x00;

        VGM_PCM_DATA tempBnk = tempPCM.bank.get(tempSht);

        byte tempByt = (byte) (DacControl.DCTRL_LMODE_BYTES |
                (mode & 0x10) |         // Reverse Mode
                ((mode & 0x01) << 7)); // Looping
        start(curChip, tempBnk.dataStart, tempByt, tempBnk.dataSize);
    }

    public void addPCMData(byte type, int dataSize, int adr, byte[] vgmBuf) {
        int curBnk;
        VGM_PCM_BANK tempPCM;
        VGM_PCM_DATA tempBnk;
        int bankSize;
        //boolean retVal;
        byte bnkType;
        byte curDAC;

        bnkType = (byte) (type & 0x3F);
        if (bnkType >= PCM_BANK_COUNT)// || vgmCurLoop > 0)
            return;

        if (type == 0x7F) {
            //ReadPCMTable(dataSize, Data);
            readPCMTable(vgmBuf, dataSize, adr);
            return;
        }

        tempPCM = pcmBank[bnkType];// &PCMBank[bnkType];
        tempPCM.bnkPos++;
        if (tempPCM.bnkPos <= tempPCM.bankCount)
            return; // Speed hack for restarting playback (skip already loaded blocks)
        curBnk = tempPCM.bankCount;
        tempPCM.bankCount++;
        //if (Last95Max != 0xFFFF) Last95Max = tempPCM.BankCount;
        tempPCM.bank.add(new VGM_PCM_DATA());// = (VGM_PCM_DATA*)realloc(tempPCM.Bank,
        // sizeof(VGM_PCM_DATA) * tempPCM.BankCount);

        if ((type & 0x40) == 0)
            bankSize = dataSize;
        else
            bankSize = getLE32(vgmBuf, adr + 1);// ReadLE32(&Data[0x01]);

        byte[] newData = new byte[tempPCM.dataSize + bankSize];
        if (tempPCM.data != null && tempPCM.data.length > 0)
            System.arraycopy(tempPCM.data, 0, newData, 0, tempPCM.data.length);
        tempPCM.data = newData;

        //tempPCM.Data = new byte[tempPCM.dataSize + bankSize];// realloc(tempPCM.Data, tempPCM.dataSize + bankSize);
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
            //tempBnk.Data = tempPCM.Data + tempBnk.dataStart;
            //memcpy(tempBnk.Data, Data, dataSize);
        } else {
            //tempBnk.Data = tempPCM.Data + tempBnk.dataStart;
            retVal = decompressDataBlk(vgmBuf, tempBnk, dataSize, adr);
            if (retVal == false) {
                tempBnk.data = null;
                tempBnk.dataSize = 0x00;
                //return;
            } else {
                // dataSize; i++)
                System.arraycopy(tempBnk.data, 0, tempPCM.data, 0 + tempBnk.dataStart, bankSize);
            }
        }
        //if (bankSize != tempBnk.dataSize) System.err.printf("Error reading Data Block! Data Size conflict!\n");
        if (retVal)
            tempPCM.dataSize += bankSize;

        // realloc may've moved the Bank block, so refresh all DAC Streams
        for (curDAC = 0x00; curDAC < dacCtrlUsed; curDAC++) {
            if (dacCtrl[dacCtrlUsg[curDAC]].bank == bnkType)
                refresh_data(dacCtrlUsg[curDAC], tempPCM.data, tempPCM.dataSize);
        }
    }

    public byte getDACFromPCMBank() {
        // for Ym2612 DAC data only
            /*VGM_PCM_BANK* TempPCM;
            (int)32 CurBnk;*/
        int DataPos;

        DataPos = pcmBank[0x00].dataPos;
        if (DataPos >= pcmBank[0x00].dataSize)
            return (byte) 0x80;

        pcmBank[0x00].dataPos++;
        return pcmBank[0x00].bank.get(0).data[DataPos];
    }

    public Integer getPCMAddressFromPCMBank(byte Type, int dataPos) {
        if (Type >= PCM_BANK_COUNT)
            return null;

        if (dataPos >= pcmBank[Type].dataSize)
            return null;

        return dataPos;
    }

    public void refresh() {
        synchronized (lockObj) {
            for (int i = 0; i < MAX_CHIPS; i++) dacData[i] = new DacControl_();
        }
    }


    private int getLE16(byte[] vgmBuf, int adr) {
        int dat;
        dat = (int) vgmBuf[adr] + (int) vgmBuf[adr + 1] * 0x100;

        return dat;
    }

    private int getLE32(byte[] vgmBuf, int adr) {
        int dat;
        dat = (int) vgmBuf[adr] + (int) vgmBuf[adr + 1] * 0x100 + (int) vgmBuf[adr + 2] * 0x10000 + (int) vgmBuf[adr + 3] * 0x1000000;

        return dat;
    }

    private boolean decompressDataBlk(byte[] vgmBuf, VGM_PCM_DATA bank, int dataSize, int adr) {
        byte comprType;
        byte bitDec;
        byte bitCmp;
        byte cmpSubType;
        int addVal;
        int inPos;
        int inDataEnd;
        int outPos;
        int outDataEnd;
        int inVal;
        int outVal = 0;// Fint outVal;
        byte valSize;
        byte inShift;
        byte outShift;
        int ent1B = 0;// (int)8* ent1B;
        int ent2B = 0;// int* ent2B;
        //#if defined(_DEBUG) && defined(WIN32)
        // (int)32 Time;
        //#endif

        // ReadBits Variables
        byte bitsToRead;
        byte bitReadVal;
        byte inValB;
        byte bitMask;
        byte outBit;

        // Variables for DPCM
        int outMask;

        //#if defined(_DEBUG) && defined(WIN32)
        // Time = GetTickCount();
        //#endif
        comprType = vgmBuf[adr + 0];
        bank.dataSize = getLE32(vgmBuf, adr + 1);

        switch (comprType) {
        case 0x00: // n-Bit compression
            bitDec = vgmBuf[adr + 5];
            bitCmp = vgmBuf[adr + 6];
            cmpSubType = vgmBuf[adr + 7];
            addVal = getLE16(vgmBuf, adr + 8);

            if (cmpSubType == 0x02) {
                //bank.dataSize = 0x00;
                //return false;

                ent1B = 0;// ((int)8*)PCMTbl.Entries; // Big Endian note: Those are stored in LE and converted when reading.
                ent2B = 0;// (int*)PCMTbl.Entries;
                if (pcmTbl.entryCount == 0) {
                    bank.dataSize = 0x00;
                    //printf("Error loading table-compressed data block! No table loaded!\n");
                    return false;
                } else if (bitDec != pcmTbl.bitDec || bitCmp != pcmTbl.bitCmp) {
                    bank.dataSize = 0x00;
                    //printf("Warning! Data block and loaded value table incompatible!\n");
                    return false;
                }
            }

            valSize = (byte) ((bitDec + 7) / 8);
            inPos = adr + 0x0A;
            inDataEnd = adr + dataSize;
            inShift = 0;
            outShift = (byte) (bitDec - bitCmp);
            //                    outDataEnd = bank.Data + bank.dataSize;
            outDataEnd = bank.dataSize;

            //for (outPos = bank.Data; outPos < outDataEnd && inPos < inDataEnd; outPos += valSize)
            for (outPos = 0; outPos < outDataEnd && inPos < inDataEnd; outPos += valSize) {
                //inVal = ReadBits(Data, inPos, &inShift, bitCmp);
                // inlined - is 30% faster
                outBit = 0x00;
                inVal = 0x0000;
                bitsToRead = bitCmp;
                while (bitsToRead != 0) {
                    bitReadVal = (byte) ((bitsToRead >= 8) ? 8 : bitsToRead);
                    bitsToRead -= bitReadVal;
                    bitMask = (byte) ((1 << bitReadVal) - 1);

                    inShift += bitReadVal;
                    //inValB = (byte)((vgmBuf[inPos] << inShift >> 8) & bitMask);
                    inValB = (byte) ((vgmBuf[inPos] << inShift >> 8) & bitMask);
                    if (inShift >= 8) {
                        inShift -= 8;
                        inPos++;
                        if (inShift != 0)
                            inValB |= (byte) ((vgmBuf[inPos] << inShift >> 8) & bitMask);
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
                        outVal = pcmTbl.entries[ent1B + inVal];
                        break;
                    case 0x02:
                        //#ifndef BIG_ENDIAN
                        //     outVal = ent2B[inVal];
                        //#else
                        outVal = pcmTbl.entries[ent2B + inVal * 2] + pcmTbl.entries[ent2B + inVal * 2 + 1] * 0x100;// ReadLE16(((int)8*)&ent2B[inVal]);
                        //#endif
                        break;
                    }
                    break;
                }

                //#ifndef BIG_ENDIAN
                //   //memcpy(outPos, &outVal, valSize);
                //   if (valSize == 0x01)
                //               *(((int)8*)outPos) = ((int)8)outVal;
                //   else //if (valSize == 0x02)
                //                *((int*)outPos) = (int)outVal;
                //#else
                if (valSize == 0x01) {
                    bank.data[outPos] = (byte) outVal;
                } else { //if (valSize == 0x02)
                    bank.data[outPos + 0x00] = (byte) ((outVal & 0x00FF) >> 0);
                    bank.data[outPos + 0x01] = (byte) ((outVal & 0xFF00) >> 8);
                }
                //#endif
            }
            break;
        case 0x01: // Delta-PCM
            bitDec = vgmBuf[adr + 5];// Data[0x05];
            bitCmp = vgmBuf[adr + 6];// Data[0x06];
            outVal = getLE16(vgmBuf, adr + 8);// ReadLE16(&Data[0x08]);

            ent1B = 0;// ((int)8*)PCMTbl.Entries;
            ent2B = 0;// (int*)PCMTbl.Entries;
            if (pcmTbl.entryCount == 0) {
                bank.dataSize = 0x00;
                //printf("Error loading table-compressed data block! No table loaded!\n");
                return false;
            } else if (bitDec != pcmTbl.bitDec || bitCmp != pcmTbl.bitCmp) {
                bank.dataSize = 0x00;
                //printf("Warning! Data block and loaded value table incompatible!\n");
                return false;
            }

            valSize = (byte) ((bitDec + 7) / 8);
            outMask = (1 << bitDec) - 1;
            inPos = adr + 0xa;
            inDataEnd = adr + dataSize;
            inShift = 0;
            outShift = (byte) (bitDec - bitCmp);
            outDataEnd = bank.dataSize;// bank.Data + bank.dataSize;
            addVal = 0x0000;

            //                    for (outPos = bank.Data; outPos < outDataEnd && inPos < inDataEnd; outPos += valSize)
            for (outPos = 0; outPos < outDataEnd && inPos < inDataEnd; outPos += valSize) {
                //inVal = ReadBits(Data, inPos, &inShift, bitCmp);
                // inlined - is 30% faster
                outBit = 0x00;
                inVal = 0x0000;
                bitsToRead = bitCmp;
                while (bitsToRead != 0) {
                    bitReadVal = (byte) ((bitsToRead >= 8) ? 8 : bitsToRead);
                    bitsToRead -= bitReadVal;
                    bitMask = (byte) ((1 << bitReadVal) - 1);

                    inShift += bitReadVal;
                    inValB = (byte) ((vgmBuf[inPos] << inShift >> 8) & bitMask);
                    if (inShift >= 8) {
                        inShift -= 8;
                        inPos++;
                        if (inShift != 0)
                            inValB |= (byte) ((vgmBuf[inPos] << inShift >> 8) & bitMask);
                    }

                    inVal |= (byte) (inValB << outBit);
                    outBit += bitReadVal;
                }

                switch (valSize) {
                case 0x01:
                    addVal = pcmTbl.entries[ent1B + inVal];
                    outVal += addVal;
                    outVal &= outMask;
                    bank.data[outPos] = (byte) outVal;// *(((int)8*)outPos) = ((int)8)outVal;
                    break;
                case 0x02:
                    //#ifndef BIG_ENDIAN
                    //    addVal = ent2B[inVal];
                    //#else
                    addVal = pcmTbl.entries[ent2B + inVal] + pcmTbl.entries[ent2B + inVal + 1] * 0x100;
                    //addVal = ReadLE16(((int)8*)&ent2B[inVal]);
                    //#endif
                    outVal += addVal;
                    outVal &= outMask;
                    //#ifndef BIG_ENDIAN
                    //    *((int*)outPos) = (int)outVal;
                    //#else
                    bank.data[outPos + 0x00] = (byte) ((outVal & 0x00FF) >> 0);
                    bank.data[outPos + 0x01] = (byte) ((outVal & 0xFF00) >> 8);
                    //#endif
                    break;
                }
            }
            break;
        default:
            //printf("Error: Unknown data block compression!\n");
            return false;
        }

        //#if defined(_DEBUG) && defined(WIN32)
        // Time = GetTickCount() - Time;
        // printf("Decompression Time: %lu\n", Time);
        //#endif

        return true;
    }

    private void readPCMTable(byte[] vgmBuf, int dataSize, int adr) {
        byte valSize;
        int tblSize;

        pcmTbl.comprType = vgmBuf[adr + 0];// Data[0x00];
        pcmTbl.cmpSubType = vgmBuf[adr + 1];// Data[0x01];
        pcmTbl.bitDec = vgmBuf[adr + 2];// Data[0x02];
        pcmTbl.bitCmp = vgmBuf[adr + 3];// Data[0x03];
        pcmTbl.entryCount = getLE16(vgmBuf, adr + 4);// ReadLE16(&Data[0x04]);

        valSize = (byte) ((pcmTbl.bitDec + 7) / 8);
        tblSize = pcmTbl.entryCount * valSize;

        pcmTbl.entries = new byte[tblSize];// realloc(PCMTbl.Entries, tblSize);
        for (int i = 0; i < tblSize; i++) pcmTbl.entries[i] = vgmBuf[adr + 6 + i];

        //if (dataSize < 0x06 + tblSize)
        //{
        //    //System.err.printf("Warning! Bad PCM Table Length!\n");
        //    //printf("Warning! Bad PCM Table Length!\n");
        //}
    }

    private void sendCommand(DacControl_ chip) {
        //注意!! chipはlock中です

        byte port;
        byte command;
        byte data;

        if ((chip.running & 0x10) != 0) // command already sent
            return;
        if (chip.dataStart + chip.realPos >= chip.dataLen)
            return;

        //if (! chip.Reverse)
        //ChipData00 = chip.data[(chip.dataStart + chip.RealPos)];
        //ChipData01 = chip.data[(chip.dataStart + chip.RealPos+1)];
        //else
        // ChipData = chip.data + (chip.dataStart + chip.CmdsToSend - 1 - chip.Pos);
        switch (chip.dstChipType) {
        // Support for the important chips
        case 0x02: // Ym2612 (16-bit Register (actually 9 Bit), 8-bit data)
            port = (byte) ((chip.dstCommand & 0xFF00) >> 8);
            command = (byte) ((chip.dstCommand & 0x00FF) >> 0);
            data = chip.data[(chip.dataStart + chip.realPos)];

            chipRegWrite(chip.dstChipType
                    , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                    , port, command, data);
            break;
        case 0x11: // PWM (4-bit Register, 12-bit data)
            port = (byte) ((chip.dstCommand & 0x000F) >> 0);
            command = (byte) (chip.data[chip.dataStart + chip.realPos + 1] & 0x0F);
            data = chip.data[chip.dataStart + chip.realPos];

            chipRegWrite(chip.dstChipType
                    , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                    , port, command, data);
            break;
        // Support for other chips (mainly for completeness)
        case 0x00: // Sn76496 (4-bit Register, 4-bit/10-bit data)
            command = (byte) ((chip.dstCommand & 0x00F0) >> 0);
            data = (byte) (chip.data[chip.dataStart + chip.realPos] & 0x0F);

            if ((command & 0x10) != 0) {
                // Volume Change (4-Bit value)
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , (byte) 0x00, (byte) 0x00, (byte) (command | data));
            } else {
                // Frequency Write (10-Bit value)
                port = (byte) (((chip.data[chip.dataStart + chip.realPos + 1] & 0x03) << 4) | ((chip.data[chip.dataStart + chip.realPos] & 0xF0) >> 4));
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , (byte) 0x00, (byte) 0x00, (byte) (command | data));
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , (byte) 0x00, (byte) 0x00, port);
            }
            break;
        case 0x18: // OKIM6295 - TODO: verify
            command = (byte) ((chip.dstCommand & 0x00FF) >> 0);
            data = chip.data[chip.dataStart + chip.realPos];

            if (command == 0) {
                port = (byte) ((chip.dstCommand & 0x0F00) >> 8);
                if ((data & 0x80) > 0) {
                    // Sample Start
                    // write sample ID
                    chipRegWrite(chip.dstChipType
                            , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                            , (byte) 0x00, command, data);
                    // write channel(s) that should play the sample
                    chipRegWrite(chip.dstChipType
                            , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                            , (byte) 0x00, command, (byte) (port << 4));
                } else {
                    // Sample Stop
                    chipRegWrite(chip.dstChipType
                            , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                            , (byte) 0x00, command, (byte) (port << 3));
                }
            } else {
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , (byte) 0x00, command, data);
            }
            break;
        // Generic support: 8-bit Register, 8-bit data
        case 0x01: // YM2413
        case 0x03: // YM2151
        case 0x06: // YM2203
        case 0x09: // YM3812
        case 0x0A: // YM3526
        case 0x0B: // Y8950
        case 0x0F: // YMZ280B
        case 0x12: // AY8910
        case 0x13: // GameBoy DMG
        case 0x14: // NES APU
            // case 0x15: // MultiPCM
        case 0x16: // UPD7759
        case 0x17: // OKIM6258
        case 0x1D: // K053260 - TODO: Verify
        case 0x1E: // Pokey - TODO: Verify
            command = (byte) ((chip.dstCommand & 0x00FF) >> 0);
            data = chip.data[chip.dataStart + chip.realPos];
            chipRegWrite(chip.dstChipType
                    , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                    , (byte) 0x00, command, data);
            break;
        // Generic support: 16-bit Register, 8-bit data
        case 0x07: // YM2608
        case 0x08: // YM2610/B
        case 0x0C: // YMF262
        case 0x0D: // YMF278B
        case 0x0E: // YMF271
        case 0x19: // K051649 - TODO: Verify
        case 0x1A: // K054539 - TODO: Verify
        case 0x1C: // C140 - TODO: Verify
            port = (byte) ((chip.dstCommand & 0xFF00) >> 8);
            command = (byte) ((chip.dstCommand & 0x00FF) >> 0);
            data = chip.data[chip.dataStart + chip.realPos];
            chipRegWrite(chip.dstChipType
                    , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                    , port, command, data);
            break;
        // Generic support: 8-bit Register with Channel Select, 8-bit data
        case 0x05: // RF5C68
        case 0x10: // RF5C164
        case 0x1B: // HuC6280
            port = (byte) ((chip.dstCommand & 0xFF00) >> 8);
            command = (byte) ((chip.dstCommand & 0x00FF) >> 0);
            data = chip.data[chip.dataStart + chip.realPos];

            if (port == 0xFF) // Send Channel Select
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , (byte) 0x00, (byte) (command & 0x0f), data);
            else {
                byte prevChn;

                prevChn = port; // by default don't restore channel
                // get current channel for supported chips
                if (chip.dstChipType == 0x05) {
                }   // TODO
                else if (chip.dstChipType == 0x05) {
                }   // TODO
                else if (chip.dstChipType == 0x1B)
                    prevChn = mds.ReadHuC6280(chip.dstChipIndex, chip.dstchipId, (byte) 0x00);

                // Send Channel Select
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , (byte) 0x00, (byte) (command >> 4), port);
                // Send data
                chipRegWrite(chip.dstChipType
                        , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                        , (byte) 0x00, (byte) (command & 0x0F), data);
                // restore old channel
                if (prevChn != port)
                    chipRegWrite(chip.dstChipType
                            , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                            , (byte) 0x00, (byte) (command >> 4), prevChn);

            }
            break;
        // Generic support: 8-bit Register, 16-bit data
        case 0x1F: // QSound
            command = (byte) ((chip.dstCommand & 0x00FF) >> 0);
            chipRegWrite(chip.dstChipType
                    , chip.dstEmuType, chip.dstChipIndex, chip.dstchipId
                    , chip.data[chip.dataStart + chip.realPos], chip.data[chip.dataStart + chip.realPos + 1], command);
            break;
        }
        chip.running |= 0x10;
    }

    private int muldiv64round(int multiplicand, int multiplier, int divisor) {
        // Yes, I'm correctly rounding the values.
        return (int) (((long) multiplicand * multiplier + divisor / 2) / divisor);
    }

    private void update(byte chipId, int samples) {
        synchronized (lockObj) {
            DacControl_ chip = dacData[chipId];
            int newPos;
            int realDataStp;

            //System.System.err.printf("DAC update chipId%d samples%d chip.Running%d ", chipId, samples, chip.Running);
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
            //System.System.err.printf("newPos%d chip.Step%d chip.DataStep%d chip.Frequency%d DAC_SMPL_RATE%d \n", newPos, chip.Step, chip.DataStep, chip.Frequency, (int)common.SampleRate);
            sendCommand(chip);

            while (chip.remainCmds != 0 && chip.pos < newPos) {
                sendCommand(chip);
                chip.pos += chip.dataStep;
                //if(model== enmModel.RealModel)                log.Write(String.format("datastep:%d",chip.DataStep));
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

    private byte deviceStartDaccontrol(byte chipId) {
        DacControl_ chip;

        if (chipId >= MAX_CHIPS)
            return 0;

        synchronized (lockObj) {
            chip = dacData[chipId];

            chip.dstChipType = (byte) 0xFF;
            chip.dstchipId = 0x00;
            chip.dstCommand = 0x0000;

            chip.running = (byte) 0xFF; // disable all actions (except setup_chip)
        }

        return 1;
    }

    public void deviceStopDaccontrol(byte chipId) {
        synchronized (lockObj) {
            DacControl_ chip = dacData[chipId];
            chip.running = (byte) 0xFF;
        }
    }

    private void deviceResetDaccontrol(byte chipId) {
        synchronized (lockObj) {
            DacControl_ chip = dacData[chipId];

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

    private void setupChip(byte chipId, byte chType, byte emuType, byte chipIndex, byte chNum, int command) {
        synchronized (lockObj) {
            DacControl_ chip = dacData[chipId];

            chip.dstChipType = chType; // TypeID (e.g. 0x02 for Ym2612)
            chip.dstEmuType = emuType;
            chip.dstChipIndex = chipIndex;
            chip.dstchipId = chNum; // chip number (to send commands to 1st or 2nd chip)
            chip.dstCommand = command; // Port and command (would be 0x02A for Ym2612)

            switch (chip.dstChipType) {
            case 0x00: // Sn76496
                if ((chip.dstCommand & 0x0010) > 0)
                    chip.cmdSize = 0x01; // Volume Write
                else
                    chip.cmdSize = 0x02; // Frequency Write
                break;
            case 0x02: // Ym2612
                chip.cmdSize = 0x01;
                break;
            case 0x11: // PWM
            case 0x1F: // QSound
                chip.cmdSize = 0x02;
                break;
            default:
                chip.cmdSize = 0x01;
                break;
            }
            chip.dataStep = (byte) (chip.cmdSize * chip.stepSize);
        }
    }

    private void setData(byte chipId, byte[] data, int dataLen, byte stepSize, byte stepBase) {
        synchronized (lockObj) {
            DacControl_ chip = dacData[chipId];

            if ((chip.running & 0x80) > 0)
                return;

            if (dataLen > 0 && data != null) {
                chip.dataLen = dataLen;
                chip.data = data;
            } else {
                chip.dataLen = 0x00;
                chip.data = null;
            }
            chip.stepSize = (byte) (stepSize > 0 ? stepSize : 1);
            chip.stepBase = stepBase;
            chip.dataStep = (byte) (chip.cmdSize * chip.stepSize);
        }
    }

    private void refresh_data(byte chipId, byte[] data, int dataLen) {
        synchronized (lockObj) {
            // Should be called to fix the data pointer. (e.g. after a realloc)
            DacControl_ chip = dacData[chipId];

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

    private void set_frequency(byte chipId, int frequency) {
        synchronized (lockObj) {
            //System.System.err.printf("chipId%d frequency%d", chipId, frequency);
            DacControl_ chip = dacData[chipId];

            if ((chip.running & 0x80) != 0)
                return;

            if (frequency != 0)
                chip.step = chip.step * chip.frequency / frequency;
            chip.frequency = frequency;
        }
    }

    private void start(byte chipId, int dataPos, byte lenMode, int length) {
        synchronized (lockObj) {
            DacControl_ chip = dacData[chipId];

            int cmdStepBase;

            if ((chip.running & 0x80) != 0)
                return;

            cmdStepBase = chip.cmdSize * chip.stepBase;
            if (dataPos != 0xFFFFFFFF) { // skip setting dataStart, if Pos == -1
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
            chip.reverse = (byte) ((lenMode & 0x10) >> 4);

            chip.remainCmds = chip.cmdsToSend;
            chip.step = 0x00;
            chip.pos = 0x00;
            if (chip.reverse == 0)
                chip.realPos = 0x00;
            else
                chip.realPos = (chip.cmdsToSend - 0x01) * chip.dataStep;

            chip.running &= 0xfb;// ~0x04;
            chip.running |= (byte) ((lenMode & 0x80) != 0 ? 0x04 : 0x00); // set loop mode

            chip.running |= 0x01; // start
            chip.running &= 0xef;// ~0x10; // command isn't yet sent
        }
    }

    private void stop(byte chipId) {
        synchronized (lockObj) {
            DacControl_ chip = dacData[chipId];

            if ((chip.running & 0x80) != 0)
                return;

            chip.running &= 0xfe;// ~0x01; // stop
        }
    }

    private void chipRegWrite(byte chipType, byte emuType, byte chipIndex, byte chipId, byte port, byte offset, byte data) {
        switch (chipType) {
        case 0x00: // SN76489
            if (emuType == 0) mds.writeSN76489(chipIndex, chipId, data);
            else if (emuType == 1) mds.writeSN76496(chipIndex, chipId, data);
            break;
        case 0x01: // YM2413+
            mds.writeYM2413(chipIndex, chipId, offset, data);
            break;
        case 0x02: // Ym2612
            if (emuType == 0) mds.writeYM2612(chipIndex, chipId, port, offset, data);
            else if (emuType == 1) mds.writeYM3438(chipIndex, chipId, port, offset, data);
            else if (emuType == 2) mds.writeYM2612Mame(chipIndex, chipId, port, offset, data);
            break;
        case 0x03: // YM2151+
            mds.writeYM2151(chipIndex, chipId, offset, data);
            break;
        case 0x06: // YM2203+
            mds.writeYM2203(chipIndex, chipId, offset, data);
            break;
        case 0x07: // YM2608+
            mds.writeYM2608(chipIndex, chipId, port, offset, data);
            break;
        case 0x08: // YM2610+
            mds.writeYM2610(chipIndex, chipId, port, offset, data);
            break;
        case 0x09: // YM3812+
            mds.writeYM3812(chipIndex, chipId, offset, data);
            break;
        case 0x0A: // YM3526+
            mds.writeYM3526(chipIndex, chipId, offset, data);
            break;
        case 0x0B: // Y8950+
            mds.writeY8950(chipIndex, chipId, offset, data);
            break;
        case 0x0C: // YMF262+
            mds.writeYMF262(chipIndex, chipId, port, offset, data);
            break;
        case 0x0D: // YMF278B+
            mds.writeYMF278B(chipIndex, chipId, port, offset, data);
            break;
        case 0x0E: // YMF271+
            mds.writeYMF271(chipIndex, chipId, port, offset, data);
            break;
        case 0x0F: // YMZ280B+
            mds.writeYMZ280B(chipIndex, chipId, offset, data);
            break;
        case 0x10:
            mds.writeRF5C164(chipIndex, chipId, offset, data);
            break;
        case 0x11: // PWM
            mds.writePWM(chipIndex, chipId, port, (offset << 8) | (data << 0));
            break;
        case 0x12: // AY8910+
            mds.writeAY8910(chipIndex, chipId, offset, data);
            break;
        case 0x13: // DMG+
            mds.writeDMG(chipIndex, chipId, offset, data);
            break;
        case 0x14: // NES+
            mds.writeNES(chipIndex, chipId, offset, data);
            break;
        case 0x17: // OKIM6258
            //System.System.err.printf("[DAC]");
            mds.writeOKIM6258(chipIndex, chipId, offset, data);
            break;
        case 0x1b: // HuC6280
            mds.writeHuC6280(chipIndex, chipId, offset, data);
            break;
        }
    }
}
