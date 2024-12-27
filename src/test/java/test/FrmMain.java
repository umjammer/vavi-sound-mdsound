package test;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileFilter;

import static java.lang.System.getLogger;

// import test.RealChip.RSoundChip;


public class FrmMain extends JFrame {

    private static final Logger logger = getLogger(FrmMain.class.getName());

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
        this.label1.setName("label1");
        this.label1.setText("Select VGM");
        // 
        // tbFile
        // 
        this.tbFile.setName("tbFile");
        this.tbFile.setColumns(32);
        //
        // btnRef
        // 
        this.btnRef.setName("btnRef");
        this.btnRef.setText("...");
        this.btnRef.addActionListener(this::btnRefClick);
        // 
        // btnPlay
        // 
        this.btnPlay.setName("btnPlay");
        this.btnPlay.setText(">");
        this.btnPlay.addActionListener(this::btnPlayClick);
        // 
        // btnStop
        // 
        this.btnStop.setText("[]");
        this.btnStop.addActionListener(this::btnStopClick);
        // 
        // label2
        // 
        this.label2.setText("||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
        // 
        // timer1
        // 
        this.timer1.scheduleAtFixedRate(this::timer1Tick, 0L, 10L, TimeUnit.SECONDS);
        // 
        // label3
        // 
        this.label3.setText("||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
        // 
        // groupBox1
        // 
//            this.groupBox1.Anchor = ((JAnchorStyles)((((JAnchorStyles.Top | JAnchorStyles.Bottom)
//            | JAnchorStyles.Left)
//            | JAnchorStyles.Right)));
        this.groupBox1.setLayout(new GridLayout(16, 2));
        this.groupBox1.setBorder(new TitledBorder("Status"));

        this.groupBox1.add(this.label12);
        this.groupBox1.add(this.lblDriverSeqCounter);
        this.groupBox1.add(this.label15);
        this.groupBox1.add(this.lblEmuSeqCounter);
        this.groupBox1.add(this.label4);
        this.groupBox1.add(this.lblSeqCounter);
        this.groupBox1.add(this.label5);
        this.groupBox1.add(this.lblDataSenderBufferCounter);

        this.groupBox1.add(new JPanel());
        this.groupBox1.add(new JPanel());

        this.groupBox1.add(this.label6);
        this.groupBox1.add(this.lblDataSenderBufferSize);
        this.groupBox1.add(this.label7);
        this.groupBox1.add(this.lblEmuChipSenderBufferSize);
        this.groupBox1.add(this.label9);
        this.groupBox1.add(this.lblRealChipSenderBufferSize);

        this.groupBox1.add(new JPanel());
        this.groupBox1.add(new JPanel());

        this.groupBox1.add(this.label8);
        this.groupBox1.add(this.lblDataMakerIsRunning);
        this.groupBox1.add(this.label11);
        this.groupBox1.add(this.lblDataSenderIsRunning);
        this.groupBox1.add(this.label10);
        this.groupBox1.add(this.lblEmuChipSenderIsRunning);
        this.groupBox1.add(this.label13);
        this.groupBox1.add(this.lblRealChipSenderIsRunning);

        this.groupBox1.add(new JPanel());
        this.groupBox1.add(new JPanel());

        this.groupBox1.add(this.label14);
        this.groupBox1.add(this.lblInterrupt);
        this.groupBox1.add(new JPanel());
        this.groupBox1.add(this.lblDebug);
        //
        // lblInterrupt
        // 
        this.lblInterrupt.setText("Disable");
        this.lblInterrupt.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // lblRealChipSenderIsRunning
        // 
        this.lblRealChipSenderIsRunning.setText("Stop");
        this.lblRealChipSenderIsRunning.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // lblEmuChipSenderIsRunning
        // 
        this.lblEmuChipSenderIsRunning.setText("Stop");
        this.lblEmuChipSenderIsRunning.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // lblDebug
        // 
        this.lblDebug.setText("debug");
        this.lblDebug.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // label14
        // 
        this.label14.setText("Interrupt :");
        // 
        // lblDataSenderIsRunning
        // 
        this.lblDataSenderIsRunning.setText("Stop");
        this.lblDataSenderIsRunning.setHorizontalAlignment(SwingConstants.RIGHT);
        //
        // label13
        // 
        this.label13.setText("RealChipSenderIsRunning :");
        // 
        // lblDataMakerIsRunning
        // 
        this.lblDataMakerIsRunning.setText("Stop");
        this.lblDataMakerIsRunning.setHorizontalAlignment(SwingConstants.RIGHT);
        // 
        // label10
        // 
        this.label10.setText("EmuChipSenderIsRunning :");
        // 
        // lblRealChipSenderBufferSize
        // 
        this.lblRealChipSenderBufferSize.setText("0");
        this.lblRealChipSenderBufferSize.setHorizontalAlignment(SwingConstants.RIGHT);
        // 
        // label11
        // 
        this.label11.setText("DataSenderIsRunning :");
        // 
        // label8
        // 
        this.label8.setText("DataMakerIsRunning :");
        // 
        // lblEmuChipSenderBufferSize
        // 
        this.lblEmuChipSenderBufferSize.setText("0");
        this.lblEmuChipSenderBufferSize.setHorizontalAlignment(SwingConstants.RIGHT);
        // 
        // label9
        // 
        this.label9.setText("RealChipSenderBufferSize :");
        // 
        // lblDataSenderBufferSize
        // 
        this.lblDataSenderBufferSize.setText("0");
        this.lblDataSenderBufferSize.setHorizontalAlignment(SwingConstants.RIGHT);
        // 
        // label7
        // 
        this.label7.setText("EmuChipSenderBufferSize :");
        // 
        // lblDataSenderBufferCounter
        // 
        this.lblDataSenderBufferCounter.setText("0");
        this.lblDataSenderBufferCounter.setHorizontalAlignment(SwingConstants.RIGHT);
        // 
        // label6
        // 
        this.label6.setText("DataSenderBufferSize :");
        // 
        // lblEmuSeqCounter
        // 
        this.lblEmuSeqCounter.setText("0");
        this.lblEmuSeqCounter.setHorizontalAlignment(SwingConstants.RIGHT);
        // 
        // lblDriverSeqCounter
        // 
        this.lblDriverSeqCounter.setText("0");
        this.lblDriverSeqCounter.setHorizontalAlignment(SwingConstants.RIGHT);
        // 
        // label15
        // 
        this.label15.setText("EmuSeqCounter :");
        // 
        // lblSeqCounter
        // 
        this.lblSeqCounter.setText("0");
        this.lblSeqCounter.setHorizontalAlignment(SwingConstants.RIGHT);
        // 
        // label12
        // 
        this.label12.setText("DriverSeqCounter :");
        // 
        // label5
        // 
        this.label5.setText("DataSenderBufferCounter :");
        // 
        // label4
        // 
        this.label4.setText("SeqCounter :");
        // 
        // FrmMain
        // 
        JPanel main = new JPanel();
        main.setLayout(new FlowLayout());
        main.setPreferredSize(new Dimension(600, 600));
        JPanel sub = new JPanel();
        sub.setLayout(new GridLayout(3, 1));
        sub.add(this.label1);
        JPanel file = new JPanel(new FlowLayout());
        file.add(this.tbFile);
        file.add(this.btnRef);
        sub.add(file);
        JPanel panel = new JPanel(new FlowLayout());
        JPanel volume = new JPanel(new GridLayout(2, 1));
        volume.add(this.label2);
        volume.add(this.label3);
        panel.add(volume);
        JPanel button = new JPanel(new FlowLayout());
        button.add(this.btnPlay);
        button.add(this.btnStop);
        panel.add(button);
        sub.add(panel);
        main.add(sub);
        main.add(this.groupBox1);
        this.getContentPane().add(main);
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
        ofd.setDialogTitle("Select a file");
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
            logger.log(Level.ERROR, e.getMessage(), e);
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
