import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * ControlGUI
 */
public class ControlGUI {

    private static final float MS_PER_SEC = 1000f;

    private static final int REPORT_INTERVAL = 2000;

    private static final String[] RUNSTATUS = { "Stopped", "Running", "Timed out" };

    private static final String STOP = "Stop";
    private static final String START = "Start";

    static final String[] BAND_NAMES = { "Delta", "Theta", "Alpha", "Beta", "Gamma" };

    private JFrame window;

    private JLabel dataRate;
    private JLabel runStatusLabel;

    long lastReportTime;
    long startTimestamp;
    int lastRecordCount;

    protected Control control;

    private JTable prebaselineTable;
    private JTable fbTable;
    private JTable postbaselineTable;

    private ChannelBandDataModel.EWMADataModel ewmaDataModel;
    private ChannelBandDataModel.PreBaselineMeanDataModel prebaselineModel;
    private ChannelBandDataModel.FeedbackMeanDataModel fbTableModel;
    private ChannelBandDataModel.PostBaselineMeanDataModel postbaselineModel;

    private JButton startStopButton;

    private JComboBox<Control.RunMode> modeBox;

    private JLabel numCutoffVals;

    private JLabel effRewardRate;

    private JLabel avgRewardAmt;

    private JTabbedPane tp;

    private JSpinner cspinner;

    private IPAddressWidget ipWidget;

    private IPAddressWidget.PortWidget fbPortWidget;

    public static void main(String[] args) throws Exception {

        new ControlGUI();
    }

    protected ControlGUI() {

        control = new Control(this);

        this.initGUI();
    }

    private void resetState() {

        lastRecordCount = 0;
        lastReportTime = 0;

        startTimestamp = System.currentTimeMillis();

        updateDataStatLabels(0);
        updateRewardStatLabels();

    }

    private void updateDataStatLabels(int dr) {

        dataRate.setText(String.valueOf(dr));
        numCutoffVals.setText(String.valueOf(control.getCuttoffRate()));
    }

    private void updateRewardStatLabels() {

        effRewardRate.setText(String.valueOf(control.getRewardRate()));
        avgRewardAmt.setText(String.valueOf(control.getRewardAmt()));

    }

    void setRunStatus(int stat) {

        switch (stat) {

            case Control.RUNNING:
                resetState();
                break;
        
            case Control.TIMED_OUT:
                dataRate.setText("0");
                break;
        }
        runStatusLabel.setText(RUNSTATUS[stat]);
    }

    private JSpinner getRewardRateSpinner() {

        JSpinner spinner = new JSpinner(new SpinnerNumberModel((int) control.targetRewardRate, -99, 99, 1));
        spinner.setPreferredSize(
                new Dimension(spinner.getPreferredSize().width - 10, spinner.getPreferredSize().height));
        spinner.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                control.targetRewardRate = (Integer) spinner.getValue();
            }

        });

        return spinner;
    }

    private JSpinner getInhibitRateSpinner(final int i) {

        JSpinner spinner = new JSpinner(new SpinnerNumberModel((int) (control.inhibitRates[i]), 10, 90, 5));

        spinner.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                control.inhibitRates[i] = (Integer) spinner.getValue();
            }

        });

        return spinner;
    }

    

    private JPanel getDataTab() {

        ewmaDataModel = new ChannelBandDataModel.EWMADataModel(control);
        prebaselineModel = new ChannelBandDataModel.PreBaselineMeanDataModel(control);
        fbTableModel = new ChannelBandDataModel.FeedbackMeanDataModel(control);
        postbaselineModel = new ChannelBandDataModel.PostBaselineMeanDataModel(control);

        JPanel panel = new JPanel();
        GridBagLayout gLayout = new GridBagLayout();

        panel.setLayout(gLayout);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets.top = 5;
        gbc.insets.bottom = 5;

        panel.add(getToolBar(), gbc);

        JLabel label = new JLabel("Pre-Baseline Means:");

        gbc.gridy=1;
        panel.add(label, gbc);

        prebaselineTable = new JTable(new ChannelBandDataModel(control));

        prebaselineTable.setPreferredScrollableViewportSize(prebaselineTable.getPreferredSize());

        JScrollPane scrollPane = new JScrollPane(prebaselineTable);

        gbc.gridy++;
        panel.add(scrollPane, gbc);

        label = new JLabel("Feedback Means:");

        gbc.gridy++;
        panel.add(label, gbc);

        fbTable = new JTable(new ChannelBandDataModel(control));

        fbTable.setTableHeader(null);
        fbTable.setPreferredScrollableViewportSize(fbTable.getPreferredSize());

        scrollPane = new JScrollPane(fbTable);

        gbc.gridy++;
        panel.add(scrollPane, gbc);

        label = new JLabel("Post-Baseline Means:");

        gbc.gridy++;
        panel.add(label, gbc);

        postbaselineTable = new JTable(new ChannelBandDataModel(control));
        postbaselineTable.setTableHeader(null);
        postbaselineTable.setPreferredScrollableViewportSize(fbTable.getPreferredSize());

        scrollPane = new JScrollPane(postbaselineTable);

        gbc.gridy++;
        panel.add(scrollPane, gbc);
        return panel;
    }

    private JPanel getConfigTab() {

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets.bottom = 10;
        gbc.insets.top = 10;
        gbc.insets.left = 10;
        gbc.insets.right = 10;
        gbc.anchor = GridBagConstraints.LINE_START;

        final JLabel numChannJLabel = new JLabel("Number Channels:");

        cspinner = new JSpinner(new SpinnerNumberModel((int) control.getNumChannels(), 4, 16, 4));

        cspinner.setPreferredSize(
                new Dimension(cspinner.getPreferredSize().width - 5, cspinner.getPreferredSize().height));
        cspinner.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                control.setNumChannels((Integer) cspinner.getValue());
                reinitTabs();
            }

        });

        gbc.gridy++;
        gbc.gridx = 0;

        panel.add(numChannJLabel, gbc);

        gbc.gridx++;
        panel.add(cspinner, gbc);

        JLabel sustainLabel = new JLabel("Criteria Sustain Duration (ms):");

        JSpinner sspinner = new JSpinner(new SpinnerNumberModel((int) control.getQualifyDuration(), 0, 1000, 100));

        sspinner.setPreferredSize(
                new Dimension(sspinner.getPreferredSize().width - 25, sspinner.getPreferredSize().height));
        sspinner.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                control.setQualifyDuration((Integer) sspinner.getValue());
            }

        });

        gbc.gridx++;

        gbc.gridwidth = 3;
        panel.add(sustainLabel, gbc);
        gbc.gridx+= gbc.gridwidth;

        gbc.gridwidth = 1;
        panel.add(sspinner, gbc);

        gbc.gridy++;

        JLabel rpLabel = new JLabel("Refractory Period Duration (ms):");

        JSpinner rpspinner = new JSpinner(new SpinnerNumberModel((int) control.getRPDuration(), 0, 1000, 100));

        rpspinner.setPreferredSize(
                new Dimension(rpspinner.getPreferredSize().width - 25, rpspinner.getPreferredSize().height));
        rpspinner.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                control.setRPDuration((Integer) rpspinner.getValue());
            }

        });

        gbc.gridx = 2;
        gbc.gridwidth = 3;
        panel.add(rpLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 5;
        panel.add(rpspinner, gbc);

        gbc.gridy++;

        JComponent[] comps = getTargetComponents();

        for (int i = 0; i < comps.length; i++) {
            gbc.gridx = i;
            panel.add(comps[i], gbc);
        }

        gbc.gridy++;

        comps = getReferenceComponents();

        for (int i = 0; i < comps.length; i++) {
            gbc.gridx = i;
            panel.add(comps[i], gbc);
        }

        gbc.gridy++;

        int ic = 0;
        do {

            comps = getInhibitComponents(ic);

            for (int i = 0; i < comps.length; i++) {
                gbc.gridx = i;
                panel.add(comps[i], gbc);
            }

            gbc.gridy++;

        } while (++ic < control.inhibitChannels.length);

        final JLabel fbAdressLbl = new JLabel("Feedback Host IP:");

        ipWidget = new IPAddressWidget(this, control.fbSendHost);

        gbc.gridx = 0;

        panel.add(fbAdressLbl, gbc);

        gbc.gridx++;
        gbc.gridwidth = 4;

        panel.add(ipWidget, gbc);

        gbc.gridwidth = 1;

        JLabel pLabel = new JLabel("Feedback Port:");

        gbc.gridx = 4;
        panel.add(pLabel, gbc);

        fbPortWidget = new IPAddressWidget.PortWidget();
        fbPortWidget.setValue(control.fbSendPort);

        fbPortWidget.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                control.fbSendPort = (int) fbPortWidget.getValue();
            }

        });

        gbc.gridx++;
        panel.add(fbPortWidget, gbc);

        return panel;
    }

    JComponent[] getReferenceComponents() {

        final JLabel rlabel = new JLabel("Reference Channel:");

        final JComboBox<String> rcbox = new JComboBox<String>(getChannelTitles(true));
        rcbox.setSelectedIndex(control.refChannel + 1);

        rcbox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (rcbox.getSelectedIndex() - 1 == control.targetChannel && control.targetBand == control.refBand) {
                    JOptionPane.showMessageDialog(rcbox, "Target and Reference cannot share same channel and band",
                            "Information", JOptionPane.INFORMATION_MESSAGE);
                    rcbox.setSelectedIndex(0); // unselect it
                }

                // set to -1 deactivates...
                control.refChannel = rcbox.getSelectedIndex() - 1;
            }
        });

        final JLabel flabel = new JLabel("Band:");

        final JComboBox<String> rbbox = new JComboBox<String>(BAND_NAMES);
        rbbox.setSelectedIndex(control.refBand);

        rbbox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                if (rbbox.getSelectedIndex() == control.targetBand && control.targetChannel == control.refChannel) {
                    JOptionPane.showMessageDialog(rbbox, "Target and Reference cannot share same channel and band",
                            "Information", JOptionPane.INFORMATION_MESSAGE);
                    int sel = rbbox.getSelectedIndex();
                    rbbox.setSelectedIndex((sel > 0) ? --sel : ++sel); // change it
                }
                control.refBand = rbbox.getSelectedIndex();
            }

        });

        return new JComponent[] { rlabel, rcbox, flabel, rbbox };
    }

    Vector<String> getChannelTitles(boolean NA) {

        Vector<String> v = new Vector<>();

        if (NA) {
            v.add("N/A");
        }

        for (int j = 1; j < control.getNumChannels() + 1; j++) {
            v.add(String.valueOf(j));
        }

        return v;
    }

    JComponent[] getTargetComponents() {

        JLabel tlabel = new JLabel("Target Channel:");

        final JComboBox<String> tcbox = new JComboBox<String>(getChannelTitles(false));
        tcbox.setSelectedIndex(control.targetChannel);

        tcbox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                control.targetChannel = tcbox.getSelectedIndex();
            }

        });

        JLabel blabel = new JLabel("Band:");

        final JComboBox<String> bbox = new JComboBox<String>(BAND_NAMES);
        bbox.setSelectedIndex(control.targetBand);

        bbox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                control.targetBand = bbox.getSelectedIndex();
            }

        });

        JLabel thlabel = new JLabel("Reward %:");

        return new JComponent[] { tlabel, tcbox, blabel, bbox, thlabel, getRewardRateSpinner() };
    }

    JComponent[] getInhibitComponents(int i) {

        JLabel ilabel = new JLabel("Inhibit Channel " + (i + 1) + ":");

        final JComboBox<String> icbox = new JComboBox<String>(getChannelTitles(true));
        icbox.setSelectedIndex(control.inhibitChannels[i] + 1);

        icbox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {

                // set to -1 deactivates...
                control.inhibitChannels[i] = icbox.getSelectedIndex() - 1;
            }
        });

        JLabel blabel = new JLabel("Band:");

        JComboBox<String> bbox = new JComboBox<String>(BAND_NAMES);
        bbox.setSelectedIndex(control.inhibitBands[i]);

        bbox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                control.inhibitBands[i] = bbox.getSelectedIndex();
            }

        });

        JLabel tlabel = new JLabel("Inhibit %:");

        return new JComponent[] { ilabel, icbox, blabel, bbox, tlabel, getInhibitRateSpinner(0) };
    }

    private void reinitTabs() {

        window.invalidate();
        window.dispose();
        initGUI();

        // refocus on configuration tab
        tp.setSelectedIndex(1);
    }

    private JPanel getToolBar() {

        startStopButton = new JButton(START);
        startStopButton.addActionListener(startStopActionListener);

        JLabel modeStatusTitle = new JLabel("Mode: ");
        modeBox = new JComboBox<Control.RunMode>(Control.RunMode.values());

        modeBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                control.setRunMode((Control.RunMode) modeBox.getSelectedItem());
            }

        });

        saveButton = new JButton("Save");
        saveButton.setEnabled(false);

        saveButton.addActionListener(new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.showSaveDialog(window);
            }
            
        });

        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEtchedBorder());

        panel.add(modeStatusTitle);
        panel.add(modeBox);
        panel.add(startStopButton);
        panel.add(saveButton);

        return panel;

    }

    private void initGUI() {

        window = new JFrame("Cortex Control");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setSize(640, 320);

        window.getContentPane().setLayout(new BorderLayout());

        tp = new JTabbedPane(JTabbedPane.TOP);

        window.add(tp, BorderLayout.CENTER);

        tp.add("Data", getDataTab());
        tp.add("Configuration", getConfigTab());

        JPanel lowerPanel = new JPanel();
        lowerPanel.setLayout(new GridBagLayout());

        lowerPanel.add(getStatusBar());

        window.getContentPane().add(lowerPanel, BorderLayout.SOUTH);

        window.pack();
        window.setVisible(true);

    }

    private JPanel getStatusBar() {

        // status bar
        JPanel panel = new JPanel();

        JLabel statusTitle = new JLabel("Status:");
        runStatusLabel = new JLabel(RUNSTATUS[Control.STOPPED]);

        panel.add(statusTitle);
        panel.add(runStatusLabel);

        panel.add(new JLabel("|"));

        dataRate = new JLabel("0");

        JLabel dataRateSuffix = new JLabel("samples/sec");

        panel.add(dataRate);
        panel.add(dataRateSuffix);

        panel.add(new JLabel("|"));

        numCutoffVals = new JLabel("0");

        JLabel cuttoffSuffix = new JLabel("% rejected");

        panel.add(numCutoffVals);
        panel.add(cuttoffSuffix);

        panel.add(new JLabel("|"));

        effRewardRate = new JLabel("0");
        JLabel rewardSuffix = new JLabel("% reward rate");

        panel.add(effRewardRate);
        panel.add(rewardSuffix);

        panel.add(new JLabel("|"));

        avgRewardAmt = new JLabel("0");
        JLabel avgRewardAmtSuffix = new JLabel("% reward amt");

        panel.add(avgRewardAmt);
        panel.add(avgRewardAmtSuffix);

        Dimension d = panel.getPreferredSize();
        d.width += 60;
        panel.setPreferredSize(d);

        return panel;
    }

    private ActionListener startStopActionListener = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {

            control.setRunning(!control.running);

            if (control.running) {

                // tool bar
                startStopButton.setText(STOP);
                modeBox.setEnabled(false);
                saveButton.setEnabled(false);

                // status bar
                setRunStatus(Control.RUNNING);

                // configuration tab
                cspinner.setEnabled(false);
                ipWidget.setEnabled(false);
                fbPortWidget.setEnabled(false);

                switch (control.runMode) {

                case PreBaseline:
                    prebaselineTable.setModel(prebaselineModel);
                    prebaselineTable.setDefaultRenderer(Object.class, new ChannelBandDataModel.ColorRenderer(control));
                    break;
                case Feedback:
                    fbTable.setModel(ewmaDataModel);
                    fbTable.setDefaultRenderer(Object.class, new ChannelBandDataModel.ColorRenderer(control));
                    break;
                case PostBaseline:
                    postbaselineTable.setModel(postbaselineModel);
                    postbaselineTable.setDefaultRenderer(Object.class, new ChannelBandDataModel.ColorRenderer(control));
                    break;

                }
            }

        }
    };

    private JButton saveButton;

    public void stopped() {

        // tool bar
        startStopButton.setText(START);
        saveButton.setEnabled(true);
        modeBox.setEnabled(true);

        // status bar
        setRunStatus(Control.STOPPED);
        dataRate.setText(String.valueOf("0"));

        // configuration tab
        cspinner.setEnabled(true);
        ipWidget.setEnabled(true);
        fbPortWidget.setEnabled(true);

        switch (control.runMode) {

        case PreBaseline:
            prebaselineTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
            prebaselineModel.fireTableDataChanged();
            break;
        case Feedback:
            fbTable.setModel(fbTableModel);
            fbTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
            fbTableModel.fireTableDataChanged();
            break;
        case PostBaseline:
            postbaselineTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
            postbaselineModel.fireTableDataChanged();
            break;

        }
    }

    /**
     * Called from non-UI thread
     */

    void doReporting() {

        long now = System.currentTimeMillis();

        // check how much time has passed since last report
        if (now - lastReportTime < REPORT_INTERVAL) {
            return;
        }

        float timeDelta = (now - lastReportTime) / MS_PER_SEC;

        int rc = control.recordCount;

        int dataRate = Math.round((rc - lastRecordCount) / timeDelta);

        updateDataStatLabels(dataRate);

        switch (control.runMode) {

        case PreBaseline:
            control.calcMeans();
            prebaselineModel.fireTableDataChanged();
            break;
        case Feedback:
            // during active feedback, we use ewma model
            ewmaDataModel.fireTableDataChanged();
            updateRewardStatLabels();
            break;
        case PostBaseline:
            control.calcMeans();
            postbaselineModel.fireTableDataChanged();
            break;

        }
        // update after the UI
        lastRecordCount = rc;
        lastReportTime = now;

    }

} // class