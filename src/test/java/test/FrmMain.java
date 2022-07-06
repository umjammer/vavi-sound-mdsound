package test;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import dotnet4j.io.File;
//import test.RealChip.RSoundChip;
import mdsound.Ay8910Mame;
import mdsound.C140;
import mdsound.C352;
import mdsound.Gb;
import mdsound.Iremga20;
import mdsound.MDSound;
import mdsound.MultiPcm;
import mdsound.NesIntF;
import mdsound.OkiM6258;
import mdsound.OkiM6295;
import mdsound.OotakePsg;
import mdsound.QSoundCtr;
import mdsound.SegaPcm;
import mdsound.Sn76496;
import mdsound.WsAudio;
import mdsound.Y8950;
import mdsound.Ym2151X68Sound;
import mdsound.Ym2203;
import mdsound.Ym2413;
import mdsound.Ym2608;
import mdsound.Ym2610;
import mdsound.Ym2612Mame;
import mdsound.Ym3526;
import mdsound.Ym3812;
import mdsound.YmF262;
import mdsound.Ymf271;
import mdsound.YmF278b;
import mdsound.YmZ280b;
import test.SoundManager.DriverAction;
import test.SoundManager.Pack;
import test.SoundManager.RingBuffer;
import test.SoundManager.SoundManager.Enq;


public class FrmMain extends JFrame {
    private void InitializeComponent() {
        this.label1 = new JLabel();
        this.tbFile = new JTextField();
        this.btnRef = new JButton();
        this.btnPlay = new JButton();
        this.btnStop = new JButton();
        this.label2 = new JLabel();
        this.timer1 = Executors.newSingleThreadScheduledExecutor();
        this.label3 = new JLabel();
        this.groupBox1 = new JPanel();
        this.lblInterrupt = new JLabel();
        this.lblRealChipSenderIsRunning = new JLabel();
        this.lblEmuChipSenderIsRunning = new JLabel();
        this.lblDebug = new JLabel();
        this.label14 = new JLabel();
        this.lblDataSenderIsRunning = new JLabel();
        this.label13 = new JLabel();
        this.lblDataMakerIsRunning = new JLabel();
        this.label10 = new JLabel();
        this.lblRealChipSenderBufferSize = new JLabel();
        this.label11 = new JLabel();
        this.label8 = new JLabel();
        this.lblEmuChipSenderBufferSize = new JLabel();
        this.label9 = new JLabel();
        this.lblDataSenderBufferSize = new JLabel();
        this.label7 = new JLabel();
        this.lblDataSenderBufferCounter = new JLabel();
        this.label6 = new JLabel();
        this.lblEmuSeqCounter = new JLabel();
        this.lblDriverSeqCounter = new JLabel();
        this.label15 = new JLabel();
        this.lblSeqCounter = new JLabel();
        this.label12 = new JLabel();
        this.label5 = new JLabel();
        this.label4 = new JLabel();
        //
        // label1
        //
//            this.label1.AutoSize = true;
        this.label1.setLocation(new Point(12, 9));
        this.label1.setName("label1");
        this.label1.setPreferredSize(new Dimension(66, 12));
//            this.label1.TabIndex = 0;
        this.label1.setText("Select VGM");
        //
        // tbFile
        //
        this.tbFile.setLocation(new Point(12, 25));
        this.tbFile.setName("tbFile");
        this.tbFile.setPreferredSize(new Dimension(286, 19));
//            this.tbFile.TabIndex = 1;
        //
        // btnRef
        //
        this.btnRef.setLocation(new Point(304, 23));
        this.btnRef.setName("btnRef");
        this.btnRef.setPreferredSize(new Dimension(25, 23));
//            this.btnRef.TabIndex = 2;
        this.btnRef.setText("...");
//            this.btnRef.UseVisualStyleBackColor = true;
        this.btnRef.addActionListener(this::BtnRef_Click);
        //
        // btnPlay
        //
        this.btnPlay.setLocation(new Point(173, 52));
        this.btnPlay.setName("btnPlay");
        this.btnPlay.setPreferredSize(new Dimension(75, 23));
//            this.btnPlay.TabIndex = 3;
        this.btnPlay.setText(">");
//            this.btnPlay.UseVisualStyleBackColor = true;
        this.btnPlay.addActionListener(this::btnPlayClick);
        //
        // btnStop
        //
        this.btnStop.setLocation(new Point(254, 52));
        this.btnStop.setName("btnStop");
        this.btnStop.setPreferredSize(new Dimension(75, 23));
//            this.btnStop.TabIndex = 4;
        this.btnStop.setText("[]");
//            this.btnStop.UseVisualStyleBackColor = true;
        this.btnStop.addActionListener(this::BtnStop_Click);
        //
        // label2
        //
//            this.label2.AutoSize = true;
        this.label2.setLocation(new Point(-174, 47));
        this.label2.setName("label2");
        this.label2.setPreferredSize(new Dimension(179, 12));
//            this.label2.TabIndex = 5;
        this.label2.setText("||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
        //
        // timer1
        //
        this.timer1.scheduleAtFixedRate(this::timer1Tick, 0l, 10l, TimeUnit.SECONDS);
        //
        // label3
        //
//            this.label3.AutoSize = true;
        this.label3.setLocation(new Point(-174, 63));
        this.label3.setName("label3");
        this.label3.setPreferredSize(new Dimension(179, 12));
//            this.label3.TabIndex = 5;
        this.label3.setText("||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
        //
        // groupBox1
        //
//            this.groupBox1.Anchor = ((JAnchorStyles)((((JAnchorStyles.Top | JAnchorStyles.Bottom)
//            | JAnchorStyles.Left)
//            | JAnchorStyles.Right)));
        this.groupBox1.add(this.lblInterrupt);
        this.groupBox1.add(this.lblRealChipSenderIsRunning);
        this.groupBox1.add(this.lblEmuChipSenderIsRunning);
        this.groupBox1.add(this.lblDebug);
        this.groupBox1.add(this.label14);
        this.groupBox1.add(this.lblDataSenderIsRunning);
        this.groupBox1.add(this.label13);
        this.groupBox1.add(this.lblDataMakerIsRunning);
        this.groupBox1.add(this.label10);
        this.groupBox1.add(this.lblRealChipSenderBufferSize);
        this.groupBox1.add(this.label11);
        this.groupBox1.add(this.label8);
        this.groupBox1.add(this.lblEmuChipSenderBufferSize);
        this.groupBox1.add(this.label9);
        this.groupBox1.add(this.lblDataSenderBufferSize);
        this.groupBox1.add(this.label7);
        this.groupBox1.add(this.lblDataSenderBufferCounter);
        this.groupBox1.add(this.label6);
        this.groupBox1.add(this.lblEmuSeqCounter);
        this.groupBox1.add(this.lblDriverSeqCounter);
        this.groupBox1.add(this.label15);
        this.groupBox1.add(this.lblSeqCounter);
        this.groupBox1.add(this.label12);
        this.groupBox1.add(this.label5);
        this.groupBox1.add(this.label4);
        this.groupBox1.setLocation(new Point(12, 81));
        this.groupBox1.setName("groupBox1");
        this.groupBox1.setPreferredSize(new Dimension(310, 218));
//            this.groupBox1.TabIndex = 6;
//            this.groupBox1.TabStop = false;
        this.groupBox1.setName("Status");
        //
        // lblInterrupt
        //
        this.lblInterrupt.setLocation(new Point(209, 174));
        this.lblInterrupt.setName("lblInterrupt");
        this.lblInterrupt.setPreferredSize(new Dimension(95, 12));
//            this.lblInterrupt.TabIndex = 0;
        this.lblInterrupt.setText("Disable");
        this.lblInterrupt.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // lblRealChipSenderIsRunning
        //
        this.lblRealChipSenderIsRunning.setLocation(new Point(209, 151));
        this.lblRealChipSenderIsRunning.setName("lblRealChipSenderIsRunning");
        this.lblRealChipSenderIsRunning.setPreferredSize(new Dimension(95, 12));
//            this.lblRealChipSenderIsRunning.TabIndex = 0;
        this.lblRealChipSenderIsRunning.setText("Stop");
        this.lblRealChipSenderIsRunning.setHorizontalAlignment(SwingConstants.CENTER);
        //
        // lblEmuChipSenderIsRunning
        //
        this.lblEmuChipSenderIsRunning.setLocation(new Point(209, 139));
        this.lblEmuChipSenderIsRunning.setName("lblEmuChipSenderIsRunning");
        this.lblEmuChipSenderIsRunning.setPreferredSize(new Dimension(95, 12));
//            this.lblEmuChipSenderIsRunning.TabIndex = 0;
        this.lblEmuChipSenderIsRunning.setText("Stop");
        this.lblEmuChipSenderIsRunning.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // lblDebug
        //
//            this.lblDebug.AutoSize = true;
        this.lblDebug.setLocation(new Point(6, 192));
        this.lblDebug.setName("lblDebug");
        this.lblDebug.setPreferredSize(new Dimension(35, 12));
//            this.lblDebug.TabIndex = 0;
        this.lblDebug.setText("debug");
        //
        // label14
        //
//            this.label14.AutoSize = true;
        this.label14.setLocation(new Point(6, 174));
        this.label14.setName("label14");
        this.label14.setPreferredSize(new Dimension(54, 12));
//            this.label14.TabIndex = 0;
        this.label14.setText("Interrupt :");
        //
        // lblDataSenderIsRunning
        //
        this.lblDataSenderIsRunning.setLocation(new Point(209, 127));
        this.lblDataSenderIsRunning.setName("lblDataSenderIsRunning");
        this.lblDataSenderIsRunning.setPreferredSize(new Dimension(95, 12));
//            this.lblDataSenderIsRunning.TabIndex = 0;
        this.lblDataSenderIsRunning.setText("Stop");
        this.lblDataSenderIsRunning.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // label13
        //
//            this.label13.AutoSize = true;
        this.label13.setLocation(new Point(6, 151));
        this.label13.setName("label13");
        this.label13.setPreferredSize(new Dimension(142, 12));
//            this.label13.TabIndex = 0;
        this.label13.setText("RealChipSenderIsRunning :");
        //
        // lblDataMakerIsRunning
        //
        this.lblDataMakerIsRunning.setLocation(new Point(209, 115));
        this.lblDataMakerIsRunning.setName("lblDataMakerIsRunning");
        this.lblDataMakerIsRunning.setPreferredSize(new Dimension(95, 12));
//            this.lblDataMakerIsRunning.TabIndex = 0;
        this.lblDataMakerIsRunning.setText("Stop");
        this.lblDataMakerIsRunning.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // label10
        //
//            this.label10.AutoSize = true;
        this.label10.setLocation(new Point(6, 139));
        this.label10.setName("label10");
        this.label10.setPreferredSize(new Dimension(141, 12));
//            this.label10.TabIndex = 0;
        this.label10.setText("EmuChipSenderIsRunning :");
        //
        // lblRealChipSenderBufferSize
        //
        this.lblRealChipSenderBufferSize.setLocation(new Point(209, 95));
        this.lblRealChipSenderBufferSize.setName("lblRealChipSenderBufferSize");
        this.lblRealChipSenderBufferSize.setPreferredSize(new Dimension(95, 12));
//            this.lblRealChipSenderBufferSize.TabIndex = 0;
        this.lblRealChipSenderBufferSize.setText("0");
        this.lblRealChipSenderBufferSize.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // label11
        //
//            this.label11.AutoSize = true;
        this.label11.setLocation(new Point(6, 127));
        this.label11.setName("label11");
        this.label11.setPreferredSize(new Dimension(120, 12));
//            this.label11.TabIndex = 0;
        this.label11.setText("DataSenderIsRunning :");
        //
        // label8
        //
//            this.label8.AutoSize = true;
        this.label8.setLocation(new Point(6, 115));
        this.label8.setName("label8");
        this.label8.setPreferredSize(new Dimension(116, 12));
//            this.label8.TabIndex = 0;
        this.label8.setText("DataMakerIsRunning :");
        //
        // lblEmuChipSenderBufferSize
        //
        this.lblEmuChipSenderBufferSize.setLocation(new Point(209, 83));
        this.lblEmuChipSenderBufferSize.setName("lblEmuChipSenderBufferSize");
        this.lblEmuChipSenderBufferSize.setPreferredSize(new Dimension(95, 12));
//            this.lblEmuChipSenderBufferSize.TabIndex = 0;
        this.lblEmuChipSenderBufferSize.setText("0");
        this.lblEmuChipSenderBufferSize.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // label9
        //
//            this.label9.AutoSize = true;
        this.label9.setLocation(new Point(6, 95));
        this.label9.setName("label9");
        this.label9.setPreferredSize(new Dimension(145, 12));
//            this.label9.TabIndex = 0;
        this.label9.setText("RealChipSenderBufferSize :");
        //
        // lblDataSenderBufferSize
        //
        this.lblDataSenderBufferSize.setLocation(new Point(209, 71));
        this.lblDataSenderBufferSize.setName("lblDataSenderBufferSize");
        this.lblDataSenderBufferSize.setPreferredSize(new Dimension(95, 12));
//            this.lblDataSenderBufferSize.TabIndex = 0;
        this.lblDataSenderBufferSize.setText("0");
        this.lblDataSenderBufferSize.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // label7
        //
//            this.label7.AutoSize = true;
        this.label7.setLocation(new Point(6, 83));
        this.label7.setName("label7");
        this.label7.setPreferredSize(new Dimension(144, 12));
//            this.label7.TabIndex = 0;
        this.label7.setText("EmuChipSenderBufferSize :");
        //
        // lblDataSenderBufferCounter
        //
        this.lblDataSenderBufferCounter.setLocation(new Point(209, 51));
        this.lblDataSenderBufferCounter.setName("lblDataSenderBufferCounter");
        this.lblDataSenderBufferCounter.setPreferredSize(new Dimension(95, 12));
//            this.lblDataSenderBufferCounter.TabIndex = 0;
        this.lblDataSenderBufferCounter.setText("0");
        this.lblDataSenderBufferCounter.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // label6
        //
//            this.label6.AutoSize = true;
        this.label6.setLocation(new Point(6, 71));
        this.label6.setName("label6");
        this.label6.setPreferredSize(new Dimension(123, 12));
//            this.label6.TabIndex = 0;
        this.label6.setText("DataSenderBufferSize :");
        //
        // lblEmuSeqCounter
        //
        this.lblEmuSeqCounter.setLocation(new Point(209, 27));
        this.lblEmuSeqCounter.setName("lblEmuSeqCounter");
        this.lblEmuSeqCounter.setPreferredSize(new Dimension(95, 12));
//            this.lblEmuSeqCounter.TabIndex = 0;
        this.lblEmuSeqCounter.setText("0");
        this.lblEmuSeqCounter.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // lblDriverSeqCounter
        //
        this.lblDriverSeqCounter.setLocation(new Point(209, 15));
        this.lblDriverSeqCounter.setName("lblDriverSeqCounter");
        this.lblDriverSeqCounter.setPreferredSize(new Dimension(95, 12));
//            this.lblDriverSeqCounter.TabIndex = 0;
        this.lblDriverSeqCounter.setText("0");
        this.lblDriverSeqCounter.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // label15
        //
//            this.label15.AutoSize = true;
        this.label15.setLocation(new Point(6, 27));
        this.label15.setName("label15");
        this.label15.setPreferredSize(new Dimension(92, 12));
//            this.label15.TabIndex = 0;
        this.label15.setText("EmuSeqCounter :");
        //
        // lblSeqCounter
        //
        this.lblSeqCounter.setLocation(new Point(209, 39));
        this.lblSeqCounter.setName("lblSeqCounter");
        this.lblSeqCounter.setPreferredSize(new Dimension(95, 12));
//            this.lblSeqCounter.TabIndex = 0;
        this.lblSeqCounter.setText("0");
        this.lblSeqCounter.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // label12
        //
//            this.label12.AutoSize = true;
        this.label12.setLocation(new Point(6, 15));
        this.label12.setName("label12");
        this.label12.setPreferredSize(new Dimension(101, 12));
//            this.label12.TabIndex = 0;
        this.label12.setText("DriverSeqCounter :");
        //
        // label5
        //
//            this.label5.AutoSize = true;
        this.label5.setLocation(new Point(6, 51));
        this.label5.setName("label5");
        this.label5.setPreferredSize(new Dimension(142, 12));
//            this.label5.TabIndex = 0;
        this.label5.setText("DataSenderBufferCounter :");
        //
        // label4
        //
//            this.label4.AutoSize = true;
        this.label4.setLocation(new Point(6, 39));
        this.label4.setName("label4");
        this.label4.setPreferredSize(new Dimension(70, 12));
//            this.label4.TabIndex = 0;
        this.label4.setText("SeqCounter :");
        //
        // FrmMain
        //
//            this.AutoScaleDimensions = new SizeF(6F, 12F);
//            this.AutoScaleMode = JAutoScaleMode.Font;
        getContentPane().setLayout(null);
        this.getContentPane().setPreferredSize(new Dimension(1920, 1080));
        this.getContentPane().add(this.groupBox1);
        this.getContentPane().add(this.label3);
        this.getContentPane().add(this.label2);
        this.getContentPane().add(this.btnStop);
        this.getContentPane().add(this.btnPlay);
        this.getContentPane().add(this.btnRef);
        this.getContentPane().add(this.tbFile);
        this.getContentPane().add(this.label1);
//            this.FormBorderStyle = JFormBorderStyle.FixedToolWindow;
        this.setName("FrmMain");
        this.setTitle("TestPlayer");
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent ev) {
                frmMainShown(ev);
            }

            @Override
            public void windowClosed(WindowEvent ev) {
                FrmMain_FormClosed(ev);
            }
        });
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//            getContentPane().setLayout(new FlowLayout());
//            pack();
        setVisible(true);
    }

    private JLabel label1;
    private JTextField tbFile;
    private JButton btnRef;
    private JButton btnPlay;
    private JButton btnStop;
    private JLabel label2;
    private ScheduledExecutorService timer1;
    private JLabel label3;
    private JPanel groupBox1;
    private ButtonGroup group1;
    private JLabel lblSeqCounter;
    private JLabel label4;
    private JLabel lblDataSenderBufferCounter;
    private JLabel label5;
    private JLabel lblDataSenderBufferSize;
    private JLabel label6;
    private JLabel lblRealChipSenderBufferSize;
    private JLabel lblEmuChipSenderBufferSize;
    private JLabel label9;
    private JLabel label7;
    private JLabel lblDataSenderIsRunning;
    private JLabel lblDataMakerIsRunning;
    private JLabel label11;
    private JLabel label8;
    private JLabel lblRealChipSenderIsRunning;
    private JLabel lblEmuChipSenderIsRunning;
    private JLabel label13;
    private JLabel label10;
    private JLabel lblEmuSeqCounter;
    private JLabel lblDriverSeqCounter;
    private JLabel label15;
    private JLabel label12;
    private JLabel lblInterrupt;
    private JLabel label14;
    private JLabel lblDebug;

    private static final int SamplingRate = 44100;
    private static final int samplingBuffer = 1024 * 8;
    private static short[] frames = new short[samplingBuffer * 4];
    private static mdsound.MDSound mds = null;

//        private static AudioStream sdl;
//        private static AudioCallback sdlCb = new AudioCallback(EmuCallback);
//        private static IntPtr sdlCbPtr;
//        private static GCHandle sdlCbHandle;

    private static byte[] vgmBuf = null;
    private static int vgmPcmPtr;
    private static int vgmPcmBaseAdr;
    private static int vgmAdr;
    private static int vgmEof;
    private static boolean vgmAnalyze;
    private static VgmStream[] vgmStreams = new VgmStream[0x100];

    private static byte[] bufYM2610AdpcmA = null;
    private static byte[] bufYM2610AdpcmB = null;

    private static class VgmStream {

        public byte chipId;
        public byte port;
        public byte cmd;

        public byte databankId;
        public byte stepsize;
        public byte stepbase;

        public int frequency;

        public int dataStartOffset;
        public byte lengthMode;
        public int dataLength;

        public boolean sw;

        public int blockId;

        public int wkDataAdr;
        public int wkDataLen;
        public double wkDataStep;
    }

    private static short[] emuRenderBuf = new short[2];
    private static long packCounter = 0;
    private static Pack pack = new Pack();

    private static long driverSeqCounter = 0;
    private static long emuSeqCounter = 0;

    private final static int PCM_BANK_COUNT = 0x40;
    private static VGM_PCM_BANK[] PCMBank = new VGM_PCM_BANK[PCM_BANK_COUNT];

    public static class VGM_PCM_DATA {
        public int DataSize;
        public byte[] Data;
        public int DataStart;
    }

    public static class VGM_PCM_BANK {
        public int BankCount;
        public List<VGM_PCM_DATA> Bank = new ArrayList<>();
        public int DataSize;
        public byte[] Data;
        public int DataPos;
        public int BnkPos;
    }

    private static test.SoundManager.SoundManager sm;
    private static Enq enq;
    //        private static RealChip rc = null;
//        private static RSoundChip rsc = null;
    private static RingBuffer emuRecvBuffer = null;


    public FrmMain() {

        InitializeComponent();

        //実チップ(OPNA)を探す(無ければrscはnull)
//            rc = new RealChip();
//            rsc = rc.SearchOPNA();
//            if (rsc != null)
//            {
//                rsc.init();
//            }

        mount();

        mds = new mdsound.MDSound(SamplingRate, samplingBuffer, null);

        mdsound.Log.level = mdsound.LogLevel.TRACE;
        mdsound.Log.writeLine = this::LogWrite;

//            sdlCbHandle = GCHandle.Alloc(sdlCb);
//            sdlCbPtr = Marshal.GetFunctionPointerForDelegate(sdlCb);
//            sdl = new SdlDotNet.Audio.AudioStream((int)SamplingRate, AudioFormat.Signed16Little, SoundChannel.Stereo, (short)samplingBuffer, sdlCb, null)
//            {
//                Paused = true
//            };
    }

    private void LogWrite(mdsound.LogLevel level, String msg) {
        Log.Write(msg);
    }


    private void BtnRef_Click(ActionEvent ev) {

        JFileChooser ofd = new JFileChooser();
//                Filter =
//                "VGMファイル(*.vgm)|*.vgm",
//                Title = "ファイルを選択してください",
//                RestoreDirectory = true,
//                CheckPathExists = true

        if (ofd.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            tbFile.setText(ofd.getSelectedFile().toString());
        }

    }

    private void btnPlayClick(ActionEvent ev) {
        btnPlay.setEnabled(false);

        sm.requestStop();
        while (sm.isRunningAsync()) {
            Thread.yield();
        }

        driverSeqCounter = sm.GetDriverSeqCounterDelay();
        driverSeqCounter = 0;

        play(tbFile.getText());

        sm.RequestStart();
        while (!sm.isRunningAsync()) ;

//            if (rsc == null)
//            {
//                sm.RequestStopAtRealChipSender();
//                while (sm.IsRunningAtRealChipSender()) ;
//            }
//            else
//            {
        sm.RequestStopAtEmuChipSender();
        while (sm.IsRunningAtEmuChipSender()) ;
//            }

        btnPlay.setEnabled(true);
    }

    private void BtnStop_Click(ActionEvent ev) {
        btnStop.setEnabled(false);

        sm.requestStop();
        while (sm.isRunningAsync()) ;

        btnStop.setEnabled(true);
    }

    private void FrmMain_FormClosed(WindowEvent ev) {

        sm.requestStop();
        while (sm.isRunningAsync()) ;

        unmount();

//            if (rc != null)
//            {
//                rc.Close();
//            }

//            if (sdl == null) return;
//
//            sdl.Paused = true;
//            sdl.Close();
//            sdl.Dispose();
//            sdl = null;
//            if (sdlCbHandle.IsAllocated) sdlCbHandle.Free();
    }

    private void timer1Tick() {
        if (mds == null) return;
        int l = mds.getTotalVolumeL();
        int r = mds.getTotalVolumeR();

        label2.setLocation(new Point(Math.min((l / 600) * 3 - 174, 0), label2.getLocation().y));
        label3.setLocation(new Point(Math.min((r / 600) * 3 - 174, 0), label3.getLocation().y));

        lblDriverSeqCounter.setText(String.valueOf(driverSeqCounter));
        lblEmuSeqCounter.setText(String.valueOf(emuSeqCounter));
        lblSeqCounter.setText(String.valueOf(sm.GetSeqCounter()));
        lblDataSenderBufferCounter.setText(String.valueOf(sm.GetDataSenderBufferCounter()));
        lblDataSenderBufferSize.setText(String.valueOf(sm.GetDataSenderBufferSize()));
        lblEmuChipSenderBufferSize.setText(String.valueOf(sm.GetEmuChipSenderBufferSize()));
        lblRealChipSenderBufferSize.setText(String.valueOf(sm.GetRealChipSenderBufferSize()));

        lblDataMakerIsRunning.setText(sm.IsRunningAtDataMaker() ? "Running" : "Stop");
        lblDataSenderIsRunning.setText(sm.IsRunningAtDataSender() ? "Running" : "Stop");
        lblEmuChipSenderIsRunning.setText(sm.IsRunningAtEmuChipSender() ? "Running" : "Stop");
        lblRealChipSenderIsRunning.setText(sm.IsRunningAtRealChipSender() ? "Running" : "Stop");

        lblInterrupt.setText(sm.GetInterrupt() ? "Enable" : "Disable");

        lblDebug.setText(mds.getDebugMsg());
    }

    private void mount() {
        sm = new test.SoundManager.SoundManager();
        DriverAction DriverAction = new DriverAction();
        DriverAction.Main = this::DriverActionMain;
        DriverAction.Final = this::DriverActionFinal;

//            if (rsc == null)
//            {
//                sm.Setup(DriverAction, null, SoftInitYM2608(0x56), SoftResetYM2608(0x56));
//            }
//            else
//            {
        sm.Setup(DriverAction, this::RealChipAction, softInitYM2608(-1), softResetYM2608(-1));
//            }

        enq = sm.GetDriverDataEnqueue();
        emuRecvBuffer = sm.GetEmuRecvBuffer();
    }

    private void DriverActionMain() {
        //OneFrameVGM();
    }

    private void DriverActionFinal() {
        Pack[] data;

//            if (rsc == null)
//            {
//                data = SoftResetYM2608(0x56);
//                DataEnq(DriverSeqCounter, 0x56, 0, -1, -1, data);
//            }
//            else
//            {
        data = softResetYM2608(-1);
        dataEnq(driverSeqCounter, -1, 0, -1, -1, data);
//            }
    }

    private void RealChipAction(long counter, int dev, int typ, int adr, int val, Object[] ex) {
        if (adr >= 0) {
//                rsc.setRegister(adr, val);
        } else {
            sm.setInterrupt();
            try {
                Pack[] data = (Pack[]) ex;
//                    for (Pack dat : data)
//                    {
//                        rsc.setRegister(dat.Adr, dat.Val);
//                    }
//                    rc.WaitOPNADPCMData(true);
            } finally {
                sm.resetInterrupt();
            }
        }
    }

    private void unmount() {
        sm.requestStop();
        while (sm.isRunningAsync()) ;
        sm.release();
    }

    public static void dataEnq(long counter, int dev, int typ, int adr, int val, Object... ex) {
        while (!enq.apply(counter, dev, typ, adr, val, ex)) Thread.yield();
    }


    private static Pack[] softInitYM2608(int dev) {
        List<Pack> data = new ArrayList<>();
        byte i;

        data.add(new Pack(dev, 0, 0x2d, 0x00));
        data.add(new Pack(dev, 0, 0x29, 0x82));
        data.add(new Pack(dev, 0, 0x07, 0x38)); //Psg TONE でリセット
        for (i = (byte) 0xb4; i < 0xb4 + 3; i++) {
            data.add(new Pack(dev, 0, i, 0xc0));
            data.add(new Pack(dev, 0, 0x100 + i, 0xc0));
        }

        return data.toArray(Pack[]::new);
    }

    private static Pack[] softResetYM2608(int dev) {
        List<Pack> data = new ArrayList<>();
        byte i;

        // FM全チャネルキーオフ
        data.add(new Pack(dev, 0, 0x28, 0x00));
        data.add(new Pack(dev, 0, 0x28, 0x01));
        data.add(new Pack(dev, 0, 0x28, 0x02));
        data.add(new Pack(dev, 0, 0x28, 0x04));
        data.add(new Pack(dev, 0, 0x28, 0x05));
        data.add(new Pack(dev, 0, 0x28, 0x06));

        // FM TL=127
        for (i = 0x40; i < 0x4F + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x7f));
            data.add(new Pack(dev, 0, 0x100 + i, 0x7f));
        }
        // FM ML/DT
        for (i = 0x30; i < 0x3F + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x0));
            data.add(new Pack(dev, 0, 0x100 + i, 0x0));
        }
        // FM AR,DR,SR,KS,AMON
        for (i = 0x50; i < 0x7F + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x0));
            data.add(new Pack(dev, 0, 0x100 + i, 0x0));
        }
        // FM SL,RR
        for (i = (byte) 0x80; i < 0x8F + 1; i++) {
            data.add(new Pack(dev, 0, i, 0xff));
            data.add(new Pack(dev, 0, 0x100 + i, 0xff));
        }
        // FM F-Num, FB/CONNECT
        for (i = (byte) 0x90; i < 0xBF + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x0));
            data.add(new Pack(dev, 0, 0x100 + i, 0x0));
        }
        // FM PAN/AMS/PMS
        for (i = (byte) 0xB4; i < 0xB6 + 1; i++) {
            data.add(new Pack(dev, 0, i, 0xc0));
            data.add(new Pack(dev, 0, 0x100 + i, 0xc0));
        }
        data.add(new Pack(dev, 0, 0x22, 0x00)); // HW LFO
        data.add(new Pack(dev, 0, 0x24, 0x00)); // Timer-A(1)
        data.add(new Pack(dev, 0, 0x25, 0x00)); // Timer-A(2)
        data.add(new Pack(dev, 0, 0x26, 0x00)); // Timer-B
        data.add(new Pack(dev, 0, 0x27, 0x30)); // Timer Controller
        data.add(new Pack(dev, 0, 0x29, 0x80)); // FM4-6 Enable

        // SSG 音程(2byte*3ch)
        for (i = 0x00; i < 0x05 + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x00));
        }
        data.add(new Pack(dev, 0, 0x06, 0x00));// SSG ノイズ周波数
        data.add(new Pack(dev, 0, 0x07, 0x38)); // SSG ミキサ
        // SSG ボリューム(3ch)
        for (i = 0x08; i < 0x0A + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x00));
        }
        // SSG Envelope
        for (i = 0x0B; i < 0x0D + 1; i++) {
            data.add(new Pack(dev, 0, i, 0x00));
        }

        // RHYTHM
        data.add(new Pack(dev, 0, 0x10, 0xBF)); // 強制発音停止
        data.add(new Pack(dev, 0, 0x11, 0x00)); // Total Level
        data.add(new Pack(dev, 0, 0x18, 0x00)); // BD音量
        data.add(new Pack(dev, 0, 0x19, 0x00)); // SD音量
        data.add(new Pack(dev, 0, 0x1A, 0x00)); // CYM音量
        data.add(new Pack(dev, 0, 0x1B, 0x00)); // HH音量
        data.add(new Pack(dev, 0, 0x1C, 0x00)); // TOM音量
        data.add(new Pack(dev, 0, 0x1D, 0x00)); // RIM音量

        // ADPCM
        data.add(new Pack(dev, 0, 0x100 + 0x00, 0x21)); // ADPCMリセット
        data.add(new Pack(dev, 0, 0x100 + 0x01, 0x06)); // ADPCM消音
        data.add(new Pack(dev, 0, 0x100 + 0x10, 0x9C)); // FLAGリセット

        return data.toArray(new Pack[data.size()]);
    }

    private static void play(String fileName) {
//            sdl.Paused = true;

        try {
            vgmBuf = File.readAllBytes(fileName);
        } catch (Exception e) {
            JOptionPane.showConfirmDialog(null, "ファイルの読み込みに失敗しました。");
            return;
        }

        for (int i = 0; i < PCMBank.length; i++) {
            PCMBank[i] = new VGM_PCM_BANK();
        }

        //ヘッダーを読み込めるサイズをもっているかチェック
        if (vgmBuf.length < 0x40) return;

        //ヘッダーから情報取得

        int vgm = getLE32(0x00);
        if (vgm != 0x206d6756) return;

        int version = getLE32(0x08);
        //if (version < 0x0150) return;

        vgmEof = getLE32(0x04);

        int vgmDataOffset = getLE32(0x34);
        if (vgmDataOffset == 0) {
            vgmDataOffset = 0x40;
        } else {
            vgmDataOffset += 0x34;
        }

        vgmAdr = vgmDataOffset;
        vgmAnalyze = true;

        mdsound.MDSound.Chip[] chips = null;
        List<mdsound.MDSound.Chip> lstChip = new ArrayList<>();
        mdsound.MDSound.Chip chip = null;

        if (getLE32(0x0c) != 0) {
            chip = new mdsound.MDSound.Chip() {{
                type = MDSound.InstrumentType.SN76489;
                id = 0;
            }};
            //mdsound.Sn76489 Sn76489 = new mdsound.Sn76489();
            Sn76496 sn76489 = new Sn76496();
            chip.instrument = sn76489;
            chip.update = sn76489::update;
            chip.start = sn76489::start;
            chip.stop = sn76489::stop;
            chip.reset = sn76489::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x0c);
            chip.volume = 0;
            if (version < 0x0150) {
                chip.option = new Object[] {
                        (byte) 9,
                        (byte) 0,
                        (byte) 16,
                        (byte) 0
                };
            } else {
                chip.option = new Object[] {
                        vgmBuf[0x28],
                        vgmBuf[0x29],
                        vgmBuf[0x2a],
                        vgmBuf[0x2b]
                };
            }
            lstChip.add(chip);
        }

        //chip = new mdsound.MDSound.Chip
        //{
        //    type = mdsound.MDSound.InstrumentType.YM2413,
        //    ID = 0
        //};
        //mdsound.SinWave sin = new mdsound.SinWave();
        //chip.Instrument = sin;
        //chip.Update = sin::Update;
        //chip.Start = sin::Start;
        //chip.Stop = sin::Stop;
        //chip.Reset = sin::Reset;
        //chip.SamplingRate = SamplingRate;
        //chip.Clock = 0;
        //chip.Volume = 0;
        //chip.Option = null;
        //lstChip.add(chip);

        if (getLE32(0x10) != 0) {
            chip = new mdsound.MDSound.Chip() {{
                type = MDSound.InstrumentType.YM2413;
                id = 0;
            }};
            Ym2413 ym2413 = new Ym2413();
            chip.instrument = ym2413;
            chip.update = ym2413::update;
            chip.start = ym2413::start;
            chip.stop = ym2413::stop;
            chip.reset = ym2413::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x10);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x2c) != 0) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.YM2612;
            //chip.type = mdsound.MDSound.InstrumentType.YM3438;
            chip.id = 0;
            //mdsound.Ym2612 Ym2612 = new mdsound.Ym2612();
            //mdsound.ym3438 Ym2612 = new mdsound.ym3438();
            Ym2612Mame ym2612 = new Ym2612Mame();
            chip.instrument = ym2612;
            chip.update = ym2612::update;
            chip.start = ym2612::start;
            chip.stop = ym2612::stop;
            chip.reset = ym2612::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x2c);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x30) != 0) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.YM2151;
            chip.id = 0;
            //mdsound.Ym2151 Ym2151 = new mdsound.Ym2151();
            //mdsound.Ym2151Mame Ym2151 = new mdsound.Ym2151Mame();
            Ym2151X68Sound ym2151 = new Ym2151X68Sound();
            chip.instrument = ym2151;
            chip.update = ym2151::update;
            chip.start = ym2151::start;
            chip.stop = ym2151::stop;
            chip.reset = ym2151::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x30);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x38) != 0 && 0x38 < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.SEGAPCM;
            chip.id = 0;
            SegaPcm segapcm = new SegaPcm();
            chip.instrument = segapcm;
            chip.update = segapcm::update;
            chip.start = segapcm::start;
            chip.stop = segapcm::stop;
            chip.reset = segapcm::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x38);
            chip.option = new Object[] {(int) getLE32(0x3c)};
            chip.volume = 0;

            lstChip.add(chip);
        }

        if (getLE32(0x44) != 0 && 0x44 < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.YM2203;
            chip.id = 0;
            Ym2203 ym2203 = new Ym2203();
            chip.instrument = ym2203;
            chip.update = ym2203::update;
            chip.start = ym2203::start;
            chip.stop = ym2203::stop;
            chip.reset = ym2203::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x44);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x48) != 0 && 0x48 < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.YM2608;
            chip.id = 0;
            Ym2608 ym2608 = new Ym2608();
            chip.instrument = ym2608;
            chip.update = ym2608::update;
            chip.start = ym2608::start;
            chip.stop = ym2608::stop;
            chip.reset = ym2608::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x48);
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }
        //if (GetLE32(0x48) != 0 && 0x48 < vgmDataOffset - 3)
        //{
        //    chip = new mdsound.MDSound.Chip();
        //    chip.type = mdsound.MDSound.InstrumentType.YM2609;
        //    chip.ID = 0;
        //    mdsound.Ym2609 Ym2609 = new mdsound.Ym2609();
        //    chip.Instrument = Ym2609;
        //    chip.Update = Ym2609::Update;
        //    chip.Start = Ym2609::Start;
        //    chip.Stop = Ym2609::Stop;
        //    chip.Reset = Ym2609::Reset;
        //    chip.SamplingRate = SamplingRate;
        //    chip.Clock = GetLE32(0x48);
        //    chip.Volume = 0;
        //    chip.Option = null;
        //    lstChip.add(chip);
        //}

        if (getLE32(0x4c) != 0 && 0x4c < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.YM2610;
            chip.id = 0;
            Ym2610 ym2610 = new Ym2610();
            chip.instrument = ym2610;
            chip.update = ym2610::update;
            chip.start = ym2610::start;
            chip.stop = ym2610::stop;
            chip.reset = ym2610::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x4c) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            bufYM2610AdpcmA = null;
            bufYM2610AdpcmB = null;
            lstChip.add(chip);
        }

        if (getLE32(0x50) != 0 && 0x50 < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.YM3812;
            chip.id = 0;
            Ym3812 ym3812 = new Ym3812();
            chip.instrument = ym3812;
            chip.update = ym3812::update;
            chip.start = ym3812::start;
            chip.stop = ym3812::stop;
            chip.reset = ym3812::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x50) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x54) != 0 && 0x54 < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.YM3526;
            chip.id = 0;
            Ym3526 ym3526 = new Ym3526();
            chip.instrument = ym3526;
            chip.update = ym3526::update;
            chip.start = ym3526::start;
            chip.stop = ym3526::stop;
            chip.reset = ym3526::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x54) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x5c) != 0 && 0x5c < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.YMF262;
            chip.id = 0;
            YmF262 ymf262 = new YmF262();
            chip.instrument = ymf262;
            chip.update = ymf262::update;
            chip.start = ymf262::start;
            chip.stop = ymf262::stop;
            chip.reset = ymf262::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x5c) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);

            //chip = new mdsound.MDSound.Chip();
            //chip.type = mdsound.MDSound.InstrumentType.YMF278B;
            //chip.ID = 0;
            //mdsound.YmF278b YmF278b = new mdsound.YmF278b();
            //chip.Instrument = YmF278b;
            //chip.Update = YmF278b.Update;
            //chip.Start = YmF278b.Start;
            //chip.Stop = YmF278b.Stop;
            //chip.Reset = YmF278b.Reset;
            //chip.SamplingRate = SamplingRate;
            //chip.Clock = getLE32(0x5c) & 0x7fffffff;
            //chip.Volume = 0;
            //chip.Option = null;
            //lstChip.add(chip);
        }

        if (getLE32(0x58) != 0 && 0x58 < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.Y8950;
            chip.id = 0;
            Y8950 y8950 = new Y8950();
            chip.instrument = y8950;
            chip.update = y8950::update;
            chip.start = y8950::start;
            chip.stop = y8950::stop;
            chip.reset = y8950::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x58) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x60) != 0 && 0x60 < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.YMF278B;
            chip.id = 0;
            YmF278b ymf278b = new YmF278b();
            chip.instrument = ymf278b;
            chip.update = ymf278b::update;
            chip.start = ymf278b::start;
            chip.stop = ymf278b::stop;
            chip.reset = ymf278b::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x60) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x64) != 0 && 0x64 < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.YMF271;
            chip.id = 0;
            Ymf271 ymf271 = new Ymf271();
            chip.instrument = ymf271;
            chip.update = ymf271::update;
            chip.start = ymf271::start;
            chip.stop = ymf271::stop;
            chip.reset = ymf271::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x64) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x68) != 0 && 0x68 < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.YMZ280B;
            chip.id = 0;
            YmZ280b ymz280b = new YmZ280b();
            chip.instrument = ymz280b;
            chip.update = ymz280b::update;
            chip.start = ymz280b::start;
            chip.stop = ymz280b::stop;
            chip.reset = ymz280b::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x68) & 0x7fffffff;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (getLE32(0x74) != 0 && 0x74 < vgmDataOffset - 3) {
            chip = new mdsound.MDSound.Chip();
            chip.type = MDSound.InstrumentType.AY8910;
            chip.id = 0;
            //mdsound.Ay8910 Ay8910 = new mdsound.Ay8910();
            Ay8910Mame ay8910 = new Ay8910Mame();
            chip.instrument = ay8910;
            chip.update = ay8910::update;
            chip.start = ay8910::start;
            chip.stop = ay8910::stop;
            chip.reset = ay8910::reset;
            chip.samplingRate = SamplingRate;
            chip.clock = getLE32(0x74) & 0x7fffffff;
            chip.clock /= 2;
            if ((vgmBuf[0x79] & 0x10) != 0)
                chip.clock /= 2;
            chip.volume = 0;
            chip.option = null;
            lstChip.add(chip);
        }

        if (version >= 0x0161 && 0x80 < vgmDataOffset - 3) {

            if (getLE32(0x80) != 0 && 0x80 < vgmDataOffset - 3) {
                chip = new mdsound.MDSound.Chip();
                chip.type = MDSound.InstrumentType.DMG;
                chip.id = 0;
                Gb gb = new Gb();
                chip.instrument = gb;
                chip.update = gb::update;
                chip.start = gb::start;
                chip.stop = gb::stop;
                chip.reset = gb::reset;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x80);// & 0x7fffffff;
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0x84) != 0 && 0x84 < vgmDataOffset - 3) {
                chip = new mdsound.MDSound.Chip();
                chip.type = MDSound.InstrumentType.Nes;
                chip.id = 0;
                NesIntF nes_intf = new NesIntF();
                chip.instrument = nes_intf;
                chip.update = nes_intf::update;
                chip.start = nes_intf::start;
                chip.stop = nes_intf::stop;
                chip.reset = nes_intf::reset;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x84);// & 0x7fffffff;
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0x88) != 0 && 0x88 < vgmDataOffset - 3) {
                chip = new mdsound.MDSound.Chip();
                chip.type = MDSound.InstrumentType.MultiPCM;
                chip.id = 0;
                MultiPcm multipcm = new MultiPcm();
                chip.instrument = multipcm;
                chip.update = multipcm::update;
                chip.start = multipcm::start;
                chip.stop = multipcm::stop;
                chip.reset = multipcm::reset;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x88) & 0x7fffffff;
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0x90) != 0 && 0x90 < vgmDataOffset - 3) {
                chip = new mdsound.MDSound.Chip();
                chip.type = MDSound.InstrumentType.OKIM6258;
                chip.id = 0;
                OkiM6258 okim6258 = new OkiM6258();
                chip.instrument = okim6258;
                chip.update = okim6258::update;
                chip.start = okim6258::start;
                chip.stop = okim6258::stop;
                chip.reset = okim6258::reset;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x90) & 0xbfffffff;
                chip.volume = 0;
                chip.option = new Object[] {(int) vgmBuf[0x94]};
                okim6258.okim6258_set_srchg_cb((byte) 0, FrmMain::ChangeChipSampleRate, chip);
                lstChip.add(chip);
            }

            if (getLE32(0x98) != 0 && 0x98 < vgmDataOffset - 3) {
                chip = new mdsound.MDSound.Chip();
                chip.type = MDSound.InstrumentType.OKIM6295;
                chip.id = 0;
                OkiM6295 okim6295 = new OkiM6295();
                chip.instrument = okim6295;
                chip.update = okim6295::update;
                chip.start = okim6295::start;
                chip.stop = okim6295::stop;
                chip.reset = okim6295::reset;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x98) & 0xbfffffff;
                chip.volume = 0;
                chip.option = null;
                okim6295.okim6295_set_srchg_cb((byte) 0, FrmMain::ChangeChipSampleRate, chip);
                lstChip.add(chip);
            }

            if (getLE32(0x9c) != 0 && 0x9c < vgmDataOffset - 3) {
                chip = new mdsound.MDSound.Chip();
                chip.type = MDSound.InstrumentType.K051649;
                chip.id = 0;
                mdsound.K051649 k051649 = new mdsound.K051649();
                chip.instrument = k051649;
                chip.update = k051649::update;
                chip.start = k051649::start;
                chip.stop = k051649::stop;
                chip.reset = k051649::reset;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0x9c);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0xa0) != 0 && 0xa0 < vgmDataOffset - 3) {
                mdsound.K054539 k054539 = new mdsound.K054539();
                int max = (getLE32(0xa0) & 0x40000000) != 0 ? 2 : 1;
                for (int i = 0; i < max; i++) {
                    chip = new mdsound.MDSound.Chip();
                    chip.type = MDSound.InstrumentType.K054539;
                    chip.id = (byte) i;
                    chip.instrument = k054539;
                    chip.update = k054539::update;
                    chip.start = k054539::start;
                    chip.stop = k054539::stop;
                    chip.reset = k054539::reset;
                    chip.samplingRate = SamplingRate;
                    chip.clock = getLE32(0xa0) & 0x3fffffff;
                    chip.volume = 0;
                    chip.option = new Object[] {vgmBuf[0x95]};

                    lstChip.add(chip);
                }
            }

            if (getLE32(0xa4) != 0 && 0xa4 < vgmDataOffset - 3) {
                chip = new mdsound.MDSound.Chip();
                chip.type = MDSound.InstrumentType.HuC6280;
                chip.id = 0;
                OotakePsg huc8910 = new OotakePsg();
                chip.instrument = huc8910;
                chip.update = huc8910::update;
                chip.start = huc8910::start;
                chip.stop = huc8910::stop;
                chip.reset = huc8910::reset;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0xa4);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0xa8) != 0 && 0xa8 < vgmDataOffset - 3) {
                chip = new mdsound.MDSound.Chip();
                chip.type = MDSound.InstrumentType.C140;
                chip.id = 0;
                C140 c140 = new C140();
                chip.instrument = c140;
                chip.update = c140::update;
                chip.start = c140::start;
                chip.stop = c140::stop;
                chip.reset = c140::reset;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0xa8);
                chip.volume = 0;
                chip.option = new Object[] {C140.C140State.Type.valueOf(vgmBuf[0x96] & 0xff)};
                lstChip.add(chip);
            }

            if (getLE32(0xac) != 0 && 0xac < vgmDataOffset - 3) {
                chip = new mdsound.MDSound.Chip();
                chip.type = MDSound.InstrumentType.K053260;
                chip.id = 0;
                mdsound.K053260 k053260 = new mdsound.K053260();
                chip.instrument = k053260;
                chip.update = k053260::update;
                chip.start = k053260::start;
                chip.stop = k053260::stop;
                chip.reset = k053260::reset;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0xac);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (getLE32(0xb4) != 0 && 0xb4 < vgmDataOffset - 3) {
                chip = new mdsound.MDSound.Chip();
                chip.type = MDSound.InstrumentType.QSound;
                chip.id = 0;
                //mdsound.QSound QSound = new mdsound.QSound();
                QSoundCtr qsound = new QSoundCtr();
                chip.instrument = qsound;
                chip.update = qsound::update;
                chip.start = qsound::start;
                chip.stop = qsound::stop;
                chip.reset = qsound::reset;
                chip.samplingRate = SamplingRate;
                chip.clock = getLE32(0xb4);
                chip.volume = 0;
                chip.option = null;
                lstChip.add(chip);
            }

            if (version >= 0x170 && 0xdc < vgmDataOffset - 3) {
                if (version >= 0x171) {
                    if (getLE32(0xc0) != 0 && 0xc0 < vgmDataOffset - 3) {
                        chip = new mdsound.MDSound.Chip();
                        chip.type = MDSound.InstrumentType.WSwan;
                        chip.id = 0;
                        WsAudio wswan = new WsAudio();
                        chip.instrument = wswan;
                        chip.update = wswan::update;
                        chip.start = wswan::start;
                        chip.stop = wswan::stop;
                        chip.reset = wswan::reset;
                        chip.samplingRate = SamplingRate;
                        chip.clock = getLE32(0xc0);
                        chip.volume = 0;
                        chip.option = null;

                        lstChip.add(chip);
                    }

                    if (getLE32(0xdc) != 0 && 0xdc < vgmDataOffset - 3) {
                        chip = new mdsound.MDSound.Chip();
                        chip.type = MDSound.InstrumentType.C352;
                        chip.id = 0;
                        C352 c352 = new C352();
                        chip.instrument = c352;
                        chip.update = c352::update;
                        chip.start = c352::start;
                        chip.stop = c352::stop;
                        chip.reset = c352::reset;
                        chip.samplingRate = SamplingRate;
                        chip.clock = getLE32(0xdc);
                        chip.volume = 0;
                        chip.option = new Object[] {vgmBuf[0xd6]};

                        lstChip.add(chip);
                    }

                    if (getLE32(0xe0) != 0 && 0xe0 < vgmDataOffset - 3) {
                        chip = new mdsound.MDSound.Chip();
                        chip.type = MDSound.InstrumentType.GA20;
                        chip.id = 0;
                        Iremga20 ga20 = new Iremga20();
                        chip.instrument = ga20;
                        chip.update = ga20::update;
                        chip.start = ga20::start;
                        chip.stop = ga20::stop;
                        chip.reset = ga20::reset;
                        chip.samplingRate = SamplingRate;
                        chip.clock = getLE32(0xe0);
                        chip.volume = 0;
                        chip.option = null;

                        lstChip.add(chip);
                    }

                }
            }
        }

        chips = lstChip.toArray(MDSound.Chip[]::new);
        mds.init(SamplingRate, samplingBuffer, chips);

//            sdl.Paused = false;
    }

    public static void ChangeChipSampleRate(mdsound.MDSound.Chip chip, int NewSmplRate) {
        mdsound.MDSound.Chip caa = chip;

        if (caa.samplingRate == NewSmplRate)
            return;

        // quick and dirty hack to make sample rate changes work
        caa.samplingRate = (int) NewSmplRate;
        if (caa.samplingRate < 44100)//SampleRate)
            caa.resampler = 0x01;
        else if (caa.samplingRate == 44100)//SampleRate)
            caa.resampler = 0x02;
        else if (caa.samplingRate > 44100)//SampleRate)
            caa.resampler = 0x03;
        caa.smpP = 1;
        caa.smpNext -= caa.smpLast;
        caa.smpLast = 0x00;
    }

    static int dummy = 0;

    private static void emuCallback(byte[] userData, byte[] stream, int len) {
        long bufCnt = len / 4;
        long seqcnt = sm.GetSeqCounter();
        emuSeqCounter = seqcnt - bufCnt;
        emuSeqCounter = Math.max(emuSeqCounter, 0);

        for (int i = 0; i < bufCnt; i++) {
            //mds.Update(emuRenderBuf, 0, 2, OneFrameVGMStream);
            mds.update(emuRenderBuf, 0, 2, FrmMain::oneFrameVGMaaa);

            frames[i * 2 + 0] = emuRenderBuf[0];
            frames[i * 2 + 1] = emuRenderBuf[1];
            //Debug.print("Adr[%8x] : Wait[%8d] : [%8d]/[%8d]\r\n", vgmAdr,0,0,0);
            //dummy++;
            //dummy %= 500;
            //frames[i * 2 + 0] = (short)dummy;// (dummy < 100 ? 0xfff : 0x000);
        }

        System.arraycopy(frames, 0, stream, 0, len / 2);
    }

    private static void oneFrameVGMaaa() {
        if (driverSeqCounter > 0) {
            driverSeqCounter--;
            return;
        }

        oneFrameVGM();
    }

    private static void oneFrameVGM() {

        if (!vgmAnalyze) {
            return;
        }

        byte p = 0;
        byte si = 0;
        byte rAdr = 0;
        byte rDat = 0;

        if (vgmAdr == vgmBuf.length || vgmAdr == vgmEof) {
            vgmAnalyze = false;
            sm.RequestStopAtDataMaker();
            return;
        }

        byte cmd = vgmBuf[vgmAdr];
        //Debug.print(" Adr[%x]:cmd[%x]\r\n", vgmAdr, cmd);
        switch (cmd & 0xff) {
        case 0x4f: //GG Psg
        case 0x50: //Psg
            mds.writeSN76489((byte) 0, vgmBuf[vgmAdr + 1]);
            //mds.WriteSN76496(0, vgmBuf[vgmAdr + 1]);
            vgmAdr += 2;
            break;
        case 0x51: //YM2413
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteYM2413(0, rAdr, rDat);
            break;
        case 0x52: //Ym2612 Port0
        case 0x53: //Ym2612 Port1
            p = (byte) ((cmd == 0x52) ? 0 : 1);
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            mds.writeYM2612((byte) 0, p, rAdr, rDat);

            break;
        case 0x54: //YM2151
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //Debug.print(" Adr[%x]:cmd[%x]:Adr[%x]:Dar[%x]\r\n", vgmAdr, cmd,rAdr,rDat);
            mds.writeYM2151((byte) 0, rAdr, rDat);
            break;
        case 0x55: //YM2203
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteYM2203(0, rAdr, rDat);

            break;
        //case 0x56: //YM2608 Port0
        //    rAdr = vgmBuf[vgmAdr + 1];
        //    rDat = vgmBuf[vgmAdr + 2];
        //    vgmAdr += 3;
        //    mds.WriteYM2608(0, 0, rAdr, rDat);

        //    break;
        //case 0x57: //YM2608 Port1
        //    rAdr = vgmBuf[vgmAdr + 1];
        //    rDat = vgmBuf[vgmAdr + 2];
        //    vgmAdr += 3;
        //    mds.WriteYM2608(0, 1, rAdr, rDat);

        //    break;
        case 0x56: //YM2609 Port0
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
//                    if (rsc == null) DataEnq(DriverSeqCounter, 0x56, 0, 0 * 0x100 + rAdr, rDat);
//                    else
            dataEnq(driverSeqCounter, -1, 0, 0 * 0x100 + rAdr, rDat);
            //mds.WriteYM2609(0, 0, rAdr, rDat);
            break;
        case 0x57: //YM2609 Port1
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
//                    if (rsc == null) DataEnq(DriverSeqCounter, 0x56, 0, 1 * 0x100 + rAdr, rDat);
//                    else
            dataEnq(driverSeqCounter, -1, 0, 1 * 0x100 + rAdr, rDat);
            //mds.WriteYM2609(0, 1, rAdr, rDat);

            break;
        case 0x58: //YM2610 Port0
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteYM2610(0, 0, rAdr, rDat);

            break;
        case 0x59: //YM2610 Port1
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteYM2610(0, 1, rAdr, rDat);

            break;
        case 0x5a: //YM3812
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteYM3812(0, rAdr, rDat);

            break;
        case 0x5b: //YM3526
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteYM3526(0, rAdr, rDat);

            break;
        case 0x5c: //Y8950
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteY8950(0, rAdr, rDat);

            break;
        case 0x5D: //YMZ280B
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteYMZ280B(0, rAdr, rDat);

            break;
        case 0x5e: //YMF262 Port0
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;

            mds.writeYMF262((byte) 0, (byte) 0, rAdr, rDat);
            //mds.WriteYMF278B(0, 0, rAdr, rDat);
            //Debug.printLine("P0:adr%2x:dat%2x", rAdr, rDat);
            break;
        case 0x5f: //YMF262 Port1
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;

            mds.writeYMF262((byte) 0, (byte) 1, rAdr, rDat);
            //mds.WriteYMF278B(0, 1, rAdr, rDat);
            //Debug.printLine("P1:adr%2x:dat%2x", rAdr, rDat);

            break;
        case 0x61: //Wait n samples
            vgmAdr++;
            driverSeqCounter += getLE16(vgmAdr);
            vgmAdr += 2;
            break;
        case 0x62: //Wait 735 samples
            vgmAdr++;
            driverSeqCounter += 735;
            break;
        case 0x63: //Wait 882 samples
            vgmAdr++;
            driverSeqCounter += 882;
            break;
        case 0x64: //@Override length of 0x62/0x63
            vgmAdr += 4;
            break;
        case 0x66: //end of Sound data
            vgmAdr = vgmBuf.length;
            break;
        case 0x67: //data block
            vgmPcmBaseAdr = vgmAdr + 7;
            int bAdr = vgmAdr + 7;
            byte bType = vgmBuf[vgmAdr + 2];
            int bLen = getLE32(vgmAdr + 3);
            //byte chipID = 0;
            if ((bLen & 0x80000000) != 0) {
                bLen &= 0x7fffffff;
                //chipID = 1;
            }

            switch (bType & 0xc0) {
            case 0x00:
            case 0x40:
                //AddPCMData(bType, bLen, bAdr);
                vgmAdr += bLen + 7;
                break;
            case 0x80:
                int romSize = getLE32(vgmAdr + 7);
                int startAddress = getLE32(vgmAdr + 0x0B);
                switch (bType & 0xff) {
                case 0x80:
                    //SEGA PCM
                    //mds.WriteSEGAPCMPCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;
                case 0x81:

                    // YM2608/YM2609
                    List<Pack> data = Arrays.asList(
                            new Pack(0, 0, 0x100 + 0x00, 0x20),
                            new Pack(0, 0, 0x100 + 0x00, 0x21),
                            new Pack(0, 0, 0x100 + 0x00, 0x00),

                            new Pack(0, 0, 0x100 + 0x10, 0x00),
                            new Pack(0, 0, 0x100 + 0x10, 0x80),

                            new Pack(0, 0, 0x100 + 0x00, 0x61),
                            new Pack(0, 0, 0x100 + 0x00, 0x68),
                            new Pack(0, 0, 0x100 + 0x01, 0x00),

                            new Pack(0, 0, 0x100 + 0x02, (byte) (startAddress >> 2)),
                            new Pack(0, 0, 0x100 + 0x03, (byte) (startAddress >> 10)),
                            new Pack(0, 0, 0x100 + 0x04, 0xff),
                            new Pack(0, 0, 0x100 + 0x05, 0xff),
                            new Pack(0, 0, 0x100 + 0x0c, 0xff),
                            new Pack(0, 0, 0x100 + 0x0d, 0xff)
                    );

                    // データ転送
                    for (int cnt = 0; cnt < bLen - 8; cnt++) {
                        data.add(new Pack(0, 0, 0x100 + 0x08, vgmBuf[vgmAdr + 15 + cnt]));
                    }
                    data.add(new Pack(0, 0, 0x100 + 0x00, 0x00));
                    data.add(new Pack(0, 0, 0x100 + 0x10, 0x80));

//                    if (rsc == null) DataEnq(DriverSeqCounter, 0x56, 0, -1, -1, data.toArray());
//                    else {
                    dataEnq(driverSeqCounter, -1, 0, -1, -1, data.toArray());
                    driverSeqCounter += bLen;
//                    }

                    break;

                case 0x82:
                    if (bufYM2610AdpcmA == null || bufYM2610AdpcmA.length != romSize)
                        bufYM2610AdpcmA = new byte[romSize];
                    for (int cnt = 0; cnt < bLen - 8; cnt++) {
                        bufYM2610AdpcmA[startAddress + cnt] = vgmBuf[vgmAdr + 15 + cnt];
                    }
                    //mds.WriteYM2610_SetAdpcmA(0, bufYM2610AdpcmA);
                    break;
                case 0x83:
                    if (bufYM2610AdpcmB == null || bufYM2610AdpcmB.length != romSize)
                        bufYM2610AdpcmB = new byte[romSize];
                    for (int cnt = 0; cnt < bLen - 8; cnt++) {
                        bufYM2610AdpcmB[startAddress + cnt] = vgmBuf[vgmAdr + 15 + cnt];
                    }
                    //mds.WriteYM2610_SetAdpcmB(0, bufYM2610AdpcmB);
                    break;

                case 0x84:
                    //mds.WriteYMF278BPCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x85:
                    //mds.WriteYMF271PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x86:
                    //mds.WriteYMZ280BPCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x88:
                    //mds.WriteY8950PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x89:
                    //mds.WriteMultiPCMPCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8b:
                    //mds.WriteOKIM6295PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8c:
                    //mds.WriteK054539PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8d:
                    //mds.WriteC140PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8e:
                    //mds.WriteK053260PCMData(chipID, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x8f:
                    mds.WriteQSoundPCMData((byte) 0, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x92:
                    //mds.WriteC352PCMData(0, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;

                case 0x93:
                    //mds.WriteGA20PCMData(0, romSize, startAddress, bLen - 8, vgmBuf, vgmAdr + 15);
                    break;
                }
                vgmAdr += bLen + 7;
                break;
            default:
                vgmAdr += bLen + 7;
                break;
            }
            break;
        case 0x68: //PCM RAM writes
            byte chipType = vgmBuf[vgmAdr + 2];
            int chipReadOffset = getLE24(vgmAdr + 3);
            int chipWriteOffset = getLE24(vgmAdr + 6);
            int chipDataSize = getLE24(vgmAdr + 9);
            if (chipDataSize == 0) chipDataSize = 0x1000000;
            Integer pcmAdr = getPCMAddressFromPCMBank(chipType, chipReadOffset);
            if (pcmAdr != null && chipType == 0x01) {
                //mds.WriteRF5C68PCMData(0, chipWriteOffset, chipDataSize, PCMBank[chipType].Data, (int)pcmAdr);
            }

            vgmAdr += 12;
            break;
        case 0x70: //Wait 1 sample
        case 0x71: //Wait 2 sample
        case 0x72: //Wait 3 sample
        case 0x73: //Wait 4 sample
        case 0x74: //Wait 5 sample
        case 0x75: //Wait 6 sample
        case 0x76: //Wait 7 sample
        case 0x77: //Wait 8 sample
        case 0x78: //Wait 9 sample
        case 0x79: //Wait 10 sample
        case 0x7a: //Wait 11 sample
        case 0x7b: //Wait 12 sample
        case 0x7c: //Wait 13 sample
        case 0x7d: //Wait 14 sample
        case 0x7e: //Wait 15 sample
        case 0x7f: //Wait 16 sample
            driverSeqCounter += (long) (cmd - 0x6f);
            vgmAdr++;
            break;
        case 0x80: //Write adr2A and Wait 0 sample
        case 0x81: //Write adr2A and Wait 1 sample
        case 0x82: //Write adr2A and Wait 2 sample
        case 0x83: //Write adr2A and Wait 3 sample
        case 0x84: //Write adr2A and Wait 4 sample
        case 0x85: //Write adr2A and Wait 5 sample
        case 0x86: //Write adr2A and Wait 6 sample
        case 0x87: //Write adr2A and Wait 7 sample
        case 0x88: //Write adr2A and Wait 8 sample
        case 0x89: //Write adr2A and Wait 9 sample
        case 0x8a: //Write adr2A and Wait 10 sample
        case 0x8b: //Write adr2A and Wait 11 sample
        case 0x8c: //Write adr2A and Wait 12 sample
        case 0x8d: //Write adr2A and Wait 13 sample
        case 0x8e: //Write adr2A and Wait 14 sample
        case 0x8f: //Write adr2A and Wait 15 sample
            mds.writeYM2612((byte) 0, (byte) 0, (byte) 0x2a, vgmBuf[vgmPcmPtr++]);
            driverSeqCounter += (long) (cmd - 0x80);
            vgmAdr++;
            break;
        case 0x90:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].chipId = vgmBuf[vgmAdr++];
            vgmStreams[si].port = vgmBuf[vgmAdr++];
            vgmStreams[si].cmd = vgmBuf[vgmAdr++];
            break;
        case 0x91:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].databankId = vgmBuf[vgmAdr++];
            vgmStreams[si].stepsize = vgmBuf[vgmAdr++];
            vgmStreams[si].stepbase = vgmBuf[vgmAdr++];
            break;
        case 0x92:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].frequency = getLE32(vgmAdr);
            vgmAdr += 4;
            break;
        case 0x93:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].dataStartOffset = getLE32(vgmAdr);
            vgmAdr += 4;
            vgmStreams[si].lengthMode = vgmBuf[vgmAdr++];
            vgmStreams[si].dataLength = getLE32(vgmAdr);
            vgmAdr += 4;

            vgmStreams[si].sw = true;
            vgmStreams[si].wkDataAdr = vgmStreams[si].dataStartOffset;
            vgmStreams[si].wkDataLen = vgmStreams[si].dataLength;
            vgmStreams[si].wkDataStep = 1.0;

            break;
        case 0x94:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].sw = false;
            break;
        case 0x95:
            vgmAdr++;
            si = vgmBuf[vgmAdr++];
            vgmStreams[si].blockId = getLE16(vgmAdr);
            vgmAdr += 2;
            p = vgmBuf[vgmAdr++];
            if ((p & 1) > 0) {
                vgmStreams[si].lengthMode |= 0x80;
            }
            if ((p & 16) > 0) {
                vgmStreams[si].lengthMode |= 0x10;
            }

            vgmStreams[si].sw = true;
            vgmStreams[si].wkDataAdr = vgmStreams[si].dataStartOffset;
            vgmStreams[si].wkDataLen = vgmStreams[si].dataLength;
            vgmStreams[si].wkDataStep = 1.0;

            break;
        case 0xa0: //AY8910
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            mds.writeAY8910((byte) 0, rAdr, rDat);

            break;
        case 0xb3: //GB DMG
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteDMG((byte) 0, rAdr, rDat);
            break;
        case 0xb4: //NES
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteNES((byte) 0, rAdr, rDat);
            break;
        case 0xb5: //MultiPCM
            rAdr = (byte) (vgmBuf[vgmAdr + 1] & 0x7f);
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteMultiPCM((byte) 0, rAdr, rDat);
            break;
        case 0xb7:
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            mds.writeOKIM6258((byte) 0, rAdr, rDat);
            break;
        case 0xb8:
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteOKIM6295(0, rAdr, rDat);
            break;
        case 0xb9: //HuC6280
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteHuC6280((byte) 0, rAdr, rDat);
            break;
        case 0xba: //K053260
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteK053260((byte) 0, rAdr, rDat);

            break;
        case 0xbc: //WSwan
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            mds.writeWSwan((byte) 0, rAdr, rDat);

            break;
        case 0xc6: //WSwan write memory
            int wsOfs = vgmBuf[vgmAdr + 1] * 0x100 + vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            vgmAdr += 4;
            mds.writeWSwanMem((byte) 0, wsOfs, rDat);

            break;
        case 0xbf: //GA20
            rAdr = vgmBuf[vgmAdr + 1];
            rDat = vgmBuf[vgmAdr + 2];
            vgmAdr += 3;
            //mds.WriteGA20(0, rAdr, rDat);

            break;
        case 0xc0://segaPCM
            //mds.WriteSEGAPCM(0, (int)((vgmBuf[vgmAdr + 0x01] & 0xFF) | ((vgmBuf[vgmAdr + 0x02] & 0xFF) << 8)), vgmBuf[vgmAdr + 0x03]);
            vgmAdr += 4;
            break;
        case 0xc3://MultiPCM
            byte multiPCM_ch = (byte) (vgmBuf[vgmAdr + 1] & 0x7f);
            int multiPCM_adr = vgmBuf[vgmAdr + 2] + vgmBuf[vgmAdr + 3] * 0x100;
            vgmAdr += 4;
            //mds.WriteMultiPCMSetBank(0, multiPCM_ch, multiPCM_adr);
            break;
        case 0xc4://QSound
            mds.WriteQSound((byte) 0, 0x00, vgmBuf[vgmAdr + 1]);
            mds.WriteQSound((byte) 0, 0x01, vgmBuf[vgmAdr + 2]);
            mds.WriteQSound((byte) 0, 0x02, vgmBuf[vgmAdr + 3]);
            //rDat = vgmBuf[vgmAdr + 3];
            //if (rsc == null) DataEnq(DriverSeqCounter, 0xc4, 0, vgmBuf[vgmAdr + 1] * 0x100 + vgmBuf[vgmAdr + 2], rDat);
            vgmAdr += 4;
            break;
        case 0xd0: //YMF278B
            byte ymf278b_port = (byte) (vgmBuf[vgmAdr + 1] & 0x7f);
            byte ymf278b_offset = vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            byte ymf278b_chipid = (byte) ((vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0);
            vgmAdr += 4;
            //mds.WriteYMF278B(ymf278b_chipid, ymf278b_port, ymf278b_offset, rDat);
            break;
        case 0xd1: //YMF271
            byte ymf271_port = (byte) (vgmBuf[vgmAdr + 1] & 0x7f);
            byte ymf271_offset = vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            byte ymf271_chipid = (byte) ((vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0);
            vgmAdr += 4;
            //mds.WriteYMF271(ymf271_chipid, ymf271_port, ymf271_offset, rDat);
            break;
        case 0xd2: //SCC1(K051649?)
            int scc1_port = vgmBuf[vgmAdr + 1] & 0x7f;
            byte scc1_offset = vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            byte scc1_chipid = (byte) ((vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0);
            vgmAdr += 4;
            //mds.WriteK051649(scc1_chipid, (scc1_port << 1) | 0x00, scc1_offset);
            //mds.WriteK051649(scc1_chipid, (scc1_port << 1) | 0x01, rDat);
            break;
        case 0xd3: //K054539
            int k054539_adr = (vgmBuf[vgmAdr + 1] & 0x7f) * 0x100 + vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            byte chipid = (byte) ((vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0);
            vgmAdr += 4;
            //mds.WriteK054539(chipid, k054539_adr, rDat);
            break;
        case 0xd4: //C140
            int c140_adr = (vgmBuf[vgmAdr + 1] & 0x7f) * 0x100 + vgmBuf[vgmAdr + 2];
            rDat = vgmBuf[vgmAdr + 3];
            byte c140_chipid = (byte) ((vgmBuf[vgmAdr + 1] & 0x80) != 0 ? 1 : 0);
            vgmAdr += 4;
            //mds.WriteC140(c140_chipid, (int)c140_adr, rDat);
            break;
        case 0xe0: //seek to offset in PCM data bank
            vgmPcmPtr = getLE32(vgmAdr + 1) + vgmPcmBaseAdr;
            vgmAdr += 5;
            break;
        case 0xe1: //C352
            int adr = (vgmBuf[vgmAdr + 1] & 0xff) * 0x100 + (vgmBuf[vgmAdr + 2] & 0xff);
            int dat = (vgmBuf[vgmAdr + 3] & 0xff) * 0x100 + (vgmBuf[vgmAdr + 4] & 0xff);
            vgmAdr += 5;
            //mds.WriteC352(0, adr, dat);

            break;
        default:
            //わからんコマンド
            System.err.printf("%02x", vgmBuf[vgmAdr++]);
        }
    }

    private static Integer getPCMAddressFromPCMBank(byte chipType, int chipReadOffset) {
        if (chipType >= PCM_BANK_COUNT)
            return null;

        if (chipReadOffset >= PCMBank[chipType].DataSize)
            return null;

        return chipReadOffset;
    }

    private static void oneFrameVGMStream() {
        while (emuRecvBuffer.lookUpCounter() <= emuSeqCounter)//&& recvBuffer.LookUpCounter() != 0)
        {
            boolean ret = emuRecvBuffer.deq(packCounter, pack.dev, pack.typ, pack.adr, pack.val, pack.ex);
            if (!ret) break;
            sendEmuData(packCounter, pack.dev, pack.typ, pack.adr, pack.val, pack.ex);
        }
        emuSeqCounter++;

        for (int i = 0; i < 0x100; i++) {

            if (!vgmStreams[i].sw) continue;
            if (vgmStreams[i].chipId != 0x02) continue;//とりあえずYM2612のみ

            while (vgmStreams[i].wkDataStep >= 1.0) {
                mds.writeYM2612((byte) 0, vgmStreams[i].port, vgmStreams[i].cmd, vgmBuf[vgmPcmBaseAdr + vgmStreams[i].wkDataAdr]);
                vgmStreams[i].wkDataAdr++;
                vgmStreams[i].dataLength--;
                vgmStreams[i].wkDataStep -= 1.0;
            }
            vgmStreams[i].wkDataStep += (double) vgmStreams[i].frequency / (double) SamplingRate;

            if (vgmStreams[i].dataLength <= 0) {
                vgmStreams[i].sw = false;
            }
        }
    }

    private static void sendEmuData(long counter, int dev, int typ, int adr, int val, Object... ex) {
        switch (dev) {
        case 0x56:
            if (adr >= 0) {
                mds.writeYM2609((byte) 0, (byte) (adr >> 8), (byte) adr, (byte) val);
            } else {
                sm.setInterrupt();
                try {
                    Pack[] data = (Pack[]) ex;
                    for (Pack dat : data) {
                        mds.writeYM2609((byte) 0, (byte) (dat.adr >> 8), (byte) dat.adr, (byte) dat.val);
                    }
                } finally {
                    sm.resetInterrupt();
                }
            }
            break;
        }
    }


    private static int getLE16(int adr) {
        int dat;
        dat = (int) vgmBuf[adr] + (int) vgmBuf[adr + 1] * 0x100;

        return dat;
    }

    private static int getLE24(int adr) {
        int dat;
        dat = (int) vgmBuf[adr] + (int) vgmBuf[adr + 1] * 0x100 + (int) vgmBuf[adr + 2] * 0x10000;

        return dat;
    }

    private static int getLE32(int adr) {
        int dat;
        dat = (int) vgmBuf[adr] + (int) vgmBuf[adr + 1] * 0x100 + (int) vgmBuf[adr + 2] * 0x10000 + (int) vgmBuf[adr + 3] * 0x1000000;

        return dat;
    }

    private void frmMainShown(WindowEvent ev) {
        String[] cmds = Program.args;

        if (cmds.length > 1) {
            tbFile.setText(cmds[1]);
            btnPlayClick(null);
        }
    }
}
