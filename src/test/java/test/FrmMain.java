package test;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileFilter;

// import test.RealChip.RSoundChip;


public class FrmMain extends JFrame {

    private void initializeComponent() {
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
        this.btnRef.addActionListener(this::btnRefClick);
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
        this.btnStop.addActionListener(this::btnStopClick);
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
        JPanel main = new JPanel();
        main.setPreferredSize(new Dimension(1920, 1080));
        main.setLayout(new FlowLayout());
        main.add(this.groupBox1);
        main.add(this.label3);
        main.add(this.label2);
        main.add(this.btnStop);
        main.add(this.btnPlay);
        main.add(this.btnRef);
        main.add(this.tbFile);
        main.add(this.label1);
        this.getContentPane().add(main);
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
                frmMainFormClosed(ev);
            }
        });
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//            getContentPane().setLayout(new FlowLayout());
        pack();
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

    Program app;

    public FrmMain() {
        initializeComponent();

        app = new Program();
    }

    static final String KEY_FILECHOOSER_DIRECTORY = "filechooser.directory";
    static Preferences prefs = Preferences.userNodeForPackage(FrmMain.class);

    private void btnRefClick(ActionEvent ev) {

        JFileChooser ofd = new JFileChooser();
        ofd.addChoosableFileFilter(new FileFilter() {
            @Override public boolean accept(java.io.File f) { return f.getName().toLowerCase().endsWith(".vgm"); }
            @Override public String getDescription() { return "VGMファイル(*.vgm)"; }
        });
        ofd.setDialogTitle("ファイルを選択してください");
        String dir = prefs.get(KEY_FILECHOOSER_DIRECTORY, null);
        if (dir != null) {
            ofd.setCurrentDirectory(new java.io.File(dir));
        }
        if (ofd.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            tbFile.setText(ofd.getSelectedFile().toString());
        }
        prefs.put(KEY_FILECHOOSER_DIRECTORY, ofd.getCurrentDirectory().getPath());
    }

    private void btnPlayClick(ActionEvent ev) {
        btnPlay.setEnabled(false);

        try {
            app.prePlay(tbFile.getText());
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "ファイルの読み込みに失敗しました。");
        }

        btnPlay.setEnabled(true);
    }

    private void btnStopClick(ActionEvent ev) {
        btnStop.setEnabled(false);

        app.stop();

        btnStop.setEnabled(true);
    }

    private void frmMainFormClosed(WindowEvent ev) {
        app.close();
    }

    private void timer1Tick() {
        if (app.mds == null) return;
        int l = app.mds.getTotalVolumeL();
        int r = app.mds.getTotalVolumeR();

        label2.setLocation(new Point(Math.min((l / 600) * 3 - 174, 0), label2.getLocation().y));
        label3.setLocation(new Point(Math.min((r / 600) * 3 - 174, 0), label3.getLocation().y));

        lblDriverSeqCounter.setText(String.valueOf(app.driverSeqCounter));
        lblEmuSeqCounter.setText(String.valueOf(app.emuSeqCounter));
        lblSeqCounter.setText(String.valueOf(app.sm.getSeqCounter()));
        lblDataSenderBufferCounter.setText(String.valueOf(app.sm.getDataSenderBufferCounter()));
        lblDataSenderBufferSize.setText(String.valueOf(app.sm.getDataSenderBufferSize()));
        lblEmuChipSenderBufferSize.setText(String.valueOf(app.sm.getEmuChipSenderBufferSize()));
        lblRealChipSenderBufferSize.setText(String.valueOf(app.sm.getRealChipSenderBufferSize()));

        lblDataMakerIsRunning.setText(app.sm.isRunningAtDataMaker() ? "Running" : "Stop");
        lblDataSenderIsRunning.setText(app.sm.isRunningAtDataSender() ? "Running" : "Stop");
        lblEmuChipSenderIsRunning.setText(app.sm.isRunningAtEmuChipSender() ? "Running" : "Stop");
        lblRealChipSenderIsRunning.setText(app.sm.isRunningAtRealChipSender() ? "Running" : "Stop");

        lblInterrupt.setText(app.sm.getInterrupt() ? "Enable" : "Disable");

        lblDebug.setText(app.mds.getDebugMsg());
    }

    private void frmMainShown(WindowEvent ev) {
        String[] cmds = Program.args;

        if (cmds.length > 1) {
            tbFile.setText(cmds[1]);
            btnPlayClick(null);
        }
    }
}
