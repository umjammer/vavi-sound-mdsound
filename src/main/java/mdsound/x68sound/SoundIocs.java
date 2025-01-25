package mdsound.x68sound;


public class SoundIocs {

    private X68Sound x68Sound = null;

    public SoundIocs(X68Sound x68Sound) {
        this.x68Sound = x68Sound;
    }

    /**
     * Returns the 16-bit value with the byte order reversed.
     */
    private static int bSwapW(int data) {
        return (data >> 8) + ((data & 0xff) << 8);
    }

    /**
     * Returns the reversed byte order of a 32-bit value.
     */
    private static int bSwapL(int adrs) {
        return (adrs >> 24) + ((adrs & 0xff0000) >> 8) + ((adrs & 0xff00) << 8) + ((adrs & 0xff) << 24);
    }

    /**
     * $02:adpcmout $12:adpcmaot $22:adpcmlot $32:adpcmcot
     */
    private int adpcmStat = 0;
    /**
     * Contents of OPM register $1B
     */
    private int opmReg1B = 0;
    private int dmaErrCode = 0;

    private int adpcmCotAdrs;
    private int adpcmCotLen;

    /**
     * Waiting for OPM to finish
     */
    private void opmWait() {
        while ((x68Sound.opmPeek() & 0x80) != 0) ;
    }

    /**
     * Processing IOCS _OPMSET ($68)
     *
     * @param addr OPM register number (0 to 255)
     * @param data Data (0 to 255)
     */
    public void opmSet(int addr, int data) {
        if (addr == 0x1B) {
            opmReg1B = (opmReg1B & 0xC0) | (data & 0x3F);
            data = opmReg1B;
        }

        opmWait();
        x68Sound.opmReg(addr);
        opmWait();
        x68Sound.opmPoke(data);
    }

    /**
     * Processing IOCS _OPMSNS ($69)
     *
     * @return bit 0: Becomes 1 when timer A overflows
     *         bit 1: Becomes 1 when timer B overflows
     *         bit 7: If it is 0, data can be written.
     */
    public int opmSns() {
        return x68Sound.opmPeek();
    }

    public Runnable opmIntProc = null; // OPM timer interrupt processing address

    /**
     * Processing IOCS _OPMINTST ($6A)
     *
     * @param addr Interrupt processing address, 0 disables interrupts
     * @return 0 if the interrupt is set
     *         If an interrupt has already been set, the interrupt processing address is returned.
     */
    public Runnable opmIntSt(Runnable addr) {
        if (addr == null) { // If the argument is 0, interrupts are disabled.
            opmIntProc = null;
            x68Sound.opmInt(opmIntProc);
            return null;
        }
        if (opmIntProc != null) { // If it is already set, it returns the processing address.
            return opmIntProc;
        }
        opmIntProc = addr;
        x68Sound.opmInt(opmIntProc); // Set the OPM interrupt processing address
        return null;
    }

    /**
     * DMA transfer end interrupt processing routine
     */
    private void dmaIntProc() {
        if (adpcmStat == 0x32 && (x68Sound.dmaPeek(0x00) & 0x40) != 0) { // Processing in continue mode
            x68Sound.dmaPoke(0x00, 0x40); // Clear BTC Bit
            if (adpcmCotLen > 0) {
                int dmalen;
                dmalen = adpcmCotLen;
                if (dmalen > 0xff00) { // The number of bytes that can be transferred at one time is 0xff00
                    dmalen = 0xff00;
                }
                x68Sound.dmaPokeL(0x1C, adpcmCotAdrs); // Set the next DMA transfer address in BAR
                x68Sound.dmaPokeW(0x1A, dmalen); // Set the next DMA transfer byte count to BTC
                adpcmCotAdrs += dmalen;
                adpcmCotLen -= dmalen;

                x68Sound.dmaPoke(0x07, 0x48); // Continue Operation Settings
            }
            return;
        }
        if ((adpcmStat & 0x80) == 0) {
            x68Sound.ppiCtrl(0x01); // ADPCM right output OFF
            x68Sound.ppiCtrl(0x03); // ADPCM left output OFF
            x68Sound.adpcmPoke(0x01); // ADPCM playback stopped
        }
        adpcmStat = 0;
        x68Sound.dmaPoke(0x00, 0xff); // Clear all bits in DMA CSR
    }

    /**
     * DMA error interrupt handling routine
     */
    private void dmaErrIntProc() {
        dmaErrCode = x68Sound.dmaPeek(0x01); // Save the error code in DmaErrCode

        x68Sound.ppiCtrl(0x01); // ADPCM right output OFF
        x68Sound.ppiCtrl(0x03); // ADPCM left output OFF
        x68Sound.adpcmPoke(0x01); // ADPCM playback stopped

        adpcmStat = 0;
        x68Sound.dmaPoke(0x00, 0xff); // Clear all bits in DMA CSR
    }

    private static final int[] PANTBL = {3, 1, 2, 0};

    /**
     * Routine to set sampling frequency and PAN and start DMA transfer
     *
     * @param mode Sampling frequency * 256 + PAN
     * @param ccr  Data to write to the DMA CCR
     */
    private void setAdpcmMode(int mode, int ccr) {
        if (mode >= 0x0200) {
            mode -= 0x0200;
            opmReg1B &= 0x7F; // ADPCM clock is 8MHz
        } else {
            opmReg1B |= 0x80; // ADPCM clock is 4MHz
        }
        opmWait();
        x68Sound.opmReg(0x1B);
        opmWait();
        x68Sound.opmPoke(opmReg1B); // ADPCM clock setting (8 or 4MHz)
        int ppiReg;
        ppiReg = (((mode >> 6) & 0x0C) | PANTBL[mode & 3]);
        ppiReg |= (x68Sound.ppiPeek() & 0xF0);
        x68Sound.dmaPoke(0x07, ccr); // DMA transfer start
        x68Sound.ppiPoke(ppiReg); // Set sampling rate & PAN to PPI
    }

    /**
     * Main routine of adpcmOut
     *
     * @param stat $80 if you want to continue DMA transfers without stopping ADPCM
     *             $00 to stop ADPCM after DMA transfer is completed
     * @param len  DMA transfer bytes
     * @param adrs DMA Transfer Address
     */
    private void adpcmOutMain(int stat, int mode, int len, int adrs) {
        while (adpcmStat != 0) ; // Wait for DMA transfer to finish
        adpcmStat = (stat + 2);
        x68Sound.dmaPoke(0x05, 0x32); // Set DMA OCR to no chain operation

        x68Sound.dmaPoke(0x00, 0xff); // Clear all bits in DMA CSR
        x68Sound.dmaPokeL(0x0c, adrs); // Set the DMA transfer address in DMA MAR
        x68Sound.dmaPokeW(0x0a, len); // Set the number of DMA transfer bytes in the DMA MTC
        setAdpcmMode(mode, 0x88); // Set the sampling frequency and PAN and start DMA transfer

        x68Sound.adpcmPoke(0x02); // ADPCM playback begins
    }

    /**
     * Processing adpcmOut ($60)
     *
     * @param addr ADPCM Data Address
     * @param mode Sampling frequency (0 to 4) * 256 + PAN (0 to 3)
     * @param len  Number of bytes of ADPCM data
     */
    public void adpcmOut(int addr, int mode, int len) {
        int dmaLen;
        int dmaAdrsPtr = addr;
        while (adpcmStat != 0) ; // Wait for DMA transfer to finish
        while (len > 0x0000_ff00) { // If the ADPCM data is 0xff00 bytes or more
            dmaLen = 0x0000_ff00; // DMA transfer is performed in multiple increments of 0xff00 bytes each
            adpcmOutMain(0x80, mode, dmaLen, dmaAdrsPtr);
            dmaAdrsPtr += dmaLen;
            len -= dmaLen;
        }
        adpcmOutMain(0x00, mode, len, dmaAdrsPtr);
    }

    /**
     * Processing IOCS_ADPCMAOT ($62)
     *
     * @param tblPtr Array chain table address
     * @param mode   Sampling frequency (0 to 4) * 256 + PAN (0 to 3)
     * @param cnt    Number of blocks in the array chain table
     */
    public void adpcmAot(int tblPtr, int mode, int cnt) {
        while (adpcmStat != 0) ; // Wait for DMA transfer to finish

        adpcmStat = 0x12;
        x68Sound.dmaPoke(0x05, 0x3A); // Set DMA OCR to array chain operation

        x68Sound.dmaPoke(0x00, 0xff); // Clear all bits in DMA CSR
        x68Sound.dmaPokeL(0x1C, tblPtr); // Set the array chain table address in the DMA BAR
        x68Sound.dmaPokeW(0x1A, cnt); // Set the number of array chain tables in the DMA BTC
        setAdpcmMode(mode, 0x88); // Set the sampling frequency and PAN and start DMA transfer

        x68Sound.adpcmPoke(0x02); // ADPCM playback begins
    }

    /**
     * Processing IOCS_ADPCMAOT ($64)
     *
     * @param tblPtr Link array chain table address
     * @param mode   Sampling frequency (0 to 4) * 256 + PAN (0 to 3)
     */
    public void adpcmLot(int tblPtr, int mode) {
        while (adpcmStat != 0) ; // Wait for DMA transfer to finish

        adpcmStat = 0x22;
        x68Sound.dmaPoke(0x05, 0x3E); // Set DMA OCR to link array chain operation

        x68Sound.dmaPoke(0x00, 0xff); // Clear all bits in DMA CSR
        x68Sound.dmaPokeL(0x1C, tblPtr); // Set the link array chain table address in the DMA BAR
        setAdpcmMode(mode, 0x88); // Set the sampling frequency and PAN and start DMA transfer

        x68Sound.adpcmPoke(0x02); // ADPCM playback begins
    }

    /**
     * A sample that uses continue mode to output ADPCM
     * Performs the same processing as IOCS_ADPCMOUT,
     * but returns immediately even if the number of data bytes is 0xff00 or more.
     *
     * @param addr ADPCM Data Address
     * @param mode Sampling frequency (0 to 4) * 256 + PAN (0 to 3)
     * @param len  Number of bytes of ADPCM data
     */
    public void adpcmCot(int addr, int mode, int len) {
        int dmaLen;
        adpcmCotAdrs = addr;
        adpcmCotLen = len;
        while (adpcmStat != 0) ; // Wait for DMA transfer to finish
        adpcmStat = 0x32;

        x68Sound.dmaPoke(0x05, 0x32); // Set DMA OCR to no chain operation

        dmaLen = adpcmCotLen;
        if (dmaLen > 0xff00) { // If the ADPCM data is 0xff00 bytes or more
            dmaLen = 0xff00; // DMA transfer is performed in multiple increments of 0xff00 bytes each
        }

        x68Sound.dmaPoke(0x00, 0xff); // Clear all bits in DMA CSR
        x68Sound.dmaPokeL(0x0C, adpcmCotAdrs); // Set the DMA transfer address in DMA MAR
        x68Sound.dmaPokeW(0x0A, dmaLen); // Set the number of DMA transfer bytes in the DMA MTC
        adpcmCotAdrs += dmaLen;
        adpcmCotLen -= dmaLen;
        if (adpcmCotLen <= 0) {
            setAdpcmMode(mode, 0x88); // If the number of data bytes is 0xff00 or less, normal transfer will occur.
        } else {
            dmaLen = adpcmCotLen;
            if (dmaLen > 0xff00) {
                dmaLen = 0xff00;
            }
            x68Sound.dmaPokeL(0x1C, adpcmCotAdrs); // Set the next DMA transfer address in BAR
            x68Sound.dmaPokeW(0x1A, dmaLen); // Set the next DMA transfer byte count to BTC
            adpcmCotAdrs += dmaLen;
            adpcmCotLen -= dmaLen;
            setAdpcmMode(mode, 0xC8); // Set the DMA CNT bit to 1 to start DMA transfer.
        }

        x68Sound.adpcmPoke(0x02); // ADPCM playback begins
    }

    /**
     * Processing IOCS _ADPCMSNS ($66)
     *
     * @return 0: it hasn't done anything.
     * $02: Outputting with _iocs_adpcmout
     * $12: Outputting with _iocs_adpcmaot
     * $22: Outputting with _iocs_adpcmlot
     * $32: Outputting with _iocs_adpcmcot
     */
    public int adpcmSns() {
        return (adpcmStat & 0x7F);
    }

    /**
     * Processing IOCS _ADPCMMOD ($67)
     *
     * @param mode 0: ADPCM Playback Stop
     *             1: ADPCM Playback Pause
     *             2: ADPCM Playback Resume
     */
    public void adpcmMod(int mode) {
        switch (mode) {
        case 0:
            adpcmStat = 0;
            x68Sound.ppiCtrl(0x01); // ADPCM right output OFF
            x68Sound.ppiCtrl(0x03); // ADPCM left output OFF
            x68Sound.adpcmPoke(0x01); // ADPCM playback stopped
            x68Sound.dmaPoke(0x07, 0x10); // DMA SAB=1 (Software Abort)
            break;
        case 1:
            x68Sound.dmaPoke(0x07, 0x20); // DMA HLT=1 (Halt operation)
            break;
        case 2:
            x68Sound.dmaPoke(0x07, 0x08); // DMA HLT=0 (Halt operation canceled)
            break;
        }
    }

    /**
     * Initializing IOCS calls
     * Set up DMA interrupts
     */
    public void init() {
        x68Sound.dmaInt(this::dmaIntProc);
        x68Sound.dmaErrInt(this::dmaErrIntProc);
    }
}

