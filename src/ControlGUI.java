import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * ControlGUI
 */
public class ControlGUI {

    private static final String[] RUNSTATUS = { "Stopped", "Running", "Timed out" };

    private static final String STOP = "STOP []";
    private static final String START = "START >";

    private static final String[] BAND_NAMES = { "Delta", "Theta", "Alpha", "Beta", "Gamma" };

    private JFrame window;

    private JLabel dataRate;
    private JLabel runStatusLabel;

    long lastReportTime = 0;
    long startTimestamp;
    int lastRecordCount;

    protected Control control;

    private JTable prebaselineTable;
    private JTable fbTable;
    private JTable postbaselineTable;

    private EWMADataModel ewmaDataModel;
    private PreBaselineMeanDataModel prebaselineModel;
    private FeedbackMeanDataModel fbTableModel;
    private PostBaselineMeanDataModel postbaselineModel;

    private JButton startStopButton;

    private JComboBox<Control.RunMode> modeBox;

    private JLabel numCutoffValsLabel;

    private JLabel effRewardRateLabel;

    public static void main(String[] args) throws Exception {

        new ControlGUI();
    }

    protected ControlGUI() {

        control = new Control(this);

        this.initGUI();
    }

    private void resetState() {

        lastRecordCount = 0;
        startTimestamp = System.currentTimeMillis();
        lastReportTime = startTimestamp;

    }

    void setRunStatus(int stat) {

        if (stat == Control.RUNNING) {
            resetState();
        }
        runStatusLabel.setText(RUNSTATUS[stat]);
    }

    private JSpinner getRewardRateSpinner() {

        JSpinner spinner = new JSpinner(new SpinnerNumberModel((int) control.targetRewardRate, -90, 90, 5));
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

    private class PortWidget extends JSpinner {

        PortWidget() {
            
            super(new SpinnerNumberModel(12345, 1, 65535, 1));
            
            setUI(new BasicSpinnerUI() {
                protected Component createNextButton() {
                    return null;
                }

                protected Component createPreviousButton() {
                    return null;
                }
            });

            ((NumberEditor)getEditor()).getFormat().setGroupingUsed(false);
            Dimension d = getPreferredSize();
                d.setSize(d.width - 25, d.getHeight());
                setPreferredSize(d);
        }

    }

    private class IPAddressWidget extends JPanel {

        Vector<JSpinner> spinners = new Vector<JSpinner>(4);

        IPAddressWidget(byte[] address) {

            FlowLayout flow = (FlowLayout) this.getLayout();
            flow.setAlignment(FlowLayout.LEFT);
            flow.setHgap(1);
            flow.setAlignOnBaseline(true);

            JSpinner spinner;
            JLabel period;

            for (int i = 0; i < 4; i++) {

                spinner = new JSpinner(new SpinnerNumberModel(control.fbSendHost[i], 0, 254, 1));
                this.add(spinner);

                if (i < 3) {
                    period = new JLabel(".");
                    period.setVerticalAlignment(SwingConstants.BOTTOM);
                    this.add(period);
                }

                spinner.setUI(new BasicSpinnerUI() {
                    protected Component createNextButton() {
                        return null;
                    }

                    protected Component createPreviousButton() {
                        return null;
                    }
                });

                // spinner
                Dimension d = spinner.getPreferredSize();
                d.setSize(d.width - 5, d.getHeight());
                spinner.setPreferredSize(d);
                spinners.add(spinner); 

                spinner.addChangeListener(new ChangeListener() {

                    @Override
                    public void stateChanged(ChangeEvent e) {

                        JSpinner s = (JSpinner) e.getSource();
                        int index = spinners.indexOf(s);
                        address[index] = (byte) (int) s.getValue();
                    }

                });


            }
        }

    }

    private JPanel getDataTab() {

        ewmaDataModel = new EWMADataModel();
        prebaselineModel = new PreBaselineMeanDataModel();
        fbTableModel = new FeedbackMeanDataModel();
        postbaselineModel = new PostBaselineMeanDataModel();

        JPanel panel = new JPanel();
        GridBagLayout gLayout = new GridBagLayout();

        panel.setLayout(gLayout);

        GridBagConstraints gbc = new GridBagConstraints();

        JLabel label = new JLabel("Pre-Baseline Means:");

        panel.add(label, gbc);

        prebaselineTable = new JTable(new BandChannelDataModel());

        prebaselineTable.setPreferredScrollableViewportSize(prebaselineTable.getPreferredSize());

        JScrollPane scrollPane = new JScrollPane(prebaselineTable);

        gbc.gridy = 1;
        panel.add(scrollPane, gbc);

        label = new JLabel("Feedback Means:");

        gbc.gridy = 2;
        panel.add(label, gbc);

        fbTable = new JTable(new BandChannelDataModel());
        fbTable.setDefaultRenderer(Object.class, new CustomCellRenderer());

        fbTable.setTableHeader(null);
        fbTable.setPreferredScrollableViewportSize(fbTable.getPreferredSize());

        scrollPane = new JScrollPane(fbTable);

        gbc.gridy = 3;
        panel.add(scrollPane, gbc);

        label = new JLabel("Post-Baseline Means:");

        gbc.gridy = 4;
        panel.add(label, gbc);

        postbaselineTable = new JTable(new BandChannelDataModel());
        postbaselineTable.setTableHeader(null);
        postbaselineTable.setPreferredScrollableViewportSize(fbTable.getPreferredSize());

        scrollPane = new JScrollPane(postbaselineTable);

        gbc.gridy = 5;
        panel.add(scrollPane, gbc);
        return panel;
    }

    private class BandChannelDataModel extends AbstractTableModel {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        @Override
        public String getColumnName(int column) {

            return BAND_NAMES[column];
        }

        @Override
        public int getRowCount() {
            return control.getNumChannels();
        }

        @Override
        public int getColumnCount() {
            return Control.NUM_BANDS;
        }

        @Override
        public Float getValueAt(int rowIndex, int columnIndex) {
            return null;
        }

    }

    private class EWMADataModel extends BandChannelDataModel {

        private static final long serialVersionUID = 1L;

        @Override
        public Float getValueAt(int rowIndex, int columnIndex) {

            return Math.round(control.bandEWMAs[rowIndex][columnIndex] * 100) / 100f;
        }

    }

    private class PreBaselineMeanDataModel extends BandChannelDataModel {

        private static final long serialVersionUID = 1L;

        @Override
        public Float getValueAt(int rowIndex, int columnIndex) {
            return Math.round(control.preBaselineMeans[rowIndex][columnIndex] * 100) / 100f;
        }
    }

    private class FeedbackMeanDataModel extends BandChannelDataModel {

        private static final long serialVersionUID = 1L;

        @Override
        public Float getValueAt(int rowIndex, int columnIndex) {
            return Math.round(control.fBMeans[rowIndex][columnIndex] * 100) / 100f;
        }
    }

    private class PostBaselineMeanDataModel extends BandChannelDataModel {

        private static final long serialVersionUID = 1L;

        @Override
        public Float getValueAt(int rowIndex, int columnIndex) {
            return Math.round(control.postBaselineMeans[rowIndex][columnIndex] * 100) / 100f;
        }
    }

    private class CustomCellRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        /**
         * @see javax.swing.table.DefaultTableCellRenderer#getTableCellRendererComponent(JTable,
         *      Object, boolean, boolean, int, int)
         */
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {

            Component rend = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value == null) {
                return rend;
            }

            float fvalue = (Float) value;

            boolean qual = control.doesValueQualify(fvalue, row, column);

            if (qual) {
                rend.setBackground(Color.GREEN);
            } else {
                rend.setBackground(Color.WHITE);
            }

            return rend;
        }

    }

    private JPanel getConfigTab() {

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets.bottom = 10;
        gbc.insets.top = 10;
        gbc.insets.left = 5;
        gbc.insets.right = 5;
        gbc.anchor = GridBagConstraints.LINE_START;

        final JLabel fbAdressLbl = new JLabel("Feedback IP:");

        IPAddressWidget ipWidget = new IPAddressWidget(control.fbSendHost);

        gbc.gridx = 0;
        gbc.gridy = 0;

        panel.add(fbAdressLbl, gbc);

        gbc.gridx++;
        gbc.gridwidth = 3;

        panel.add(ipWidget, gbc);

        gbc.gridwidth = 1;

        JLabel pLabel = new JLabel("Feedback Port:");

        gbc.gridx = 3;
        panel.add(pLabel, gbc);

        PortWidget portWidget = new PortWidget();
        portWidget.setValue(control.fbSendPort);

        portWidget.addChangeListener(new ChangeListener(){

            @Override
            public void stateChanged(ChangeEvent e) {
                control.fbSendPort = (int) portWidget.getValue();
            }
            
        });

        gbc.gridx++;
        panel.add(portWidget, gbc);

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

        JLabel sustainLabel = new JLabel("Sustainability:");

        JSpinner sspinner = new JSpinner(new SpinnerNumberModel((int) control.minNumConsecQualifiers, 1, 10, 1));

        sspinner.setPreferredSize(
                new Dimension(sspinner.getPreferredSize().width - 5, sspinner.getPreferredSize().height));
        sspinner.addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                control.minNumConsecQualifiers = (Integer) sspinner.getValue();
            }

        });

        gbc.gridx++;
        panel.add(sustainLabel, gbc);

        gbc.gridx++;
        panel.add(sspinner, gbc);

        gbc.gridy++;
        gbc.gridx = 0;

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

        final JLabel flabel = new JLabel("Freq. Band:");

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

        JLabel blabel = new JLabel("Freq. Band:");

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

        JLabel blabel = new JLabel("Freq. Band:");

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

    private void initGUI() {

        window = new JFrame("Cortex Control");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setSize(640, 320);

        window.getContentPane().setLayout(new BorderLayout());

        tp = new JTabbedPane();

        window.add(tp, BorderLayout.CENTER);

        tp.add("Data", getDataTab());
        tp.add("Configuration", getConfigTab());

        JPanel lowerPanel = new JPanel();
        lowerPanel.setLayout(new GridBagLayout());

        window.getContentPane().add(lowerPanel, BorderLayout.SOUTH);

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

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets.bottom = 5;
        gbc.insets.top = 5;
        gbc.insets.left = 2;
        gbc.insets.right = 2;

        gbc.anchor = GridBagConstraints.LINE_END;

        gbc.gridx = 0;
        gbc.weightx = .5;
        lowerPanel.add(modeStatusTitle, gbc);

        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.weightx = 0;
        gbc.gridx = 1;
        lowerPanel.add(modeBox, gbc);

        gbc.gridx = 2;
        gbc.weightx = .5;
        lowerPanel.add(startStopButton, gbc);

        // status bar
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        lowerPanel.add(getStatusBar(), gbc);

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

        numCutoffValsLabel = new JLabel("0");

        JLabel cuttoffSuffix = new JLabel("% rejected");

        panel.add(numCutoffValsLabel);
        panel.add(cuttoffSuffix);

        panel.add(new JLabel("|"));

        effRewardRateLabel = new JLabel("0");
        JLabel rewardSuffix = new JLabel("% reward rate");

        panel.add(effRewardRateLabel);
        panel.add(rewardSuffix);

        return panel;
    }

    private ActionListener startStopActionListener = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent e) {

            control.setRunning(!control.running);

            if (control.running) {

                cspinner.setEnabled(false);
                startStopButton.setText(STOP);
                setRunStatus(Control.RUNNING);
                modeBox.setEnabled(false);

                switch (control.runMode) {

                case PreBaseline:
                    prebaselineTable.setModel(ewmaDataModel);
                    break;
                case Feedback:
                    fbTable.setModel(ewmaDataModel);
                    break;
                case PostBaseline:
                    postbaselineTable.setModel(ewmaDataModel);
                    break;

                }
            }

        }
    };

    private JTabbedPane tp;

    private JSpinner cspinner;

    public void stopped() {

        startStopButton.setText(START);
        setRunStatus(Control.STOPPED);
        dataRate.setText(String.valueOf("0"));
        modeBox.setEnabled(true);
        cspinner.setEnabled(true);

        switch (control.runMode) {

        case PreBaseline:
            prebaselineTable.setModel(prebaselineModel);
            prebaselineModel.fireTableDataChanged();
            break;
        case Feedback:
            fbTable.setModel(fbTableModel);
            prebaselineModel.fireTableDataChanged();
            break;
        case PostBaseline:
            postbaselineTable.setModel(postbaselineModel);
            prebaselineModel.fireTableDataChanged();
            break;

        }
    }

    private void reportDataRate(long now) {

        float f = (now - lastReportTime) / 1000f;

        int dr = Math.round((control.recordCount - lastRecordCount) / f);

        lastRecordCount = control.recordCount;

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                dataRate.setText(String.valueOf(dr));
                numCutoffValsLabel.setText(String.valueOf(control.cuttOffRate));
            }

        });
    }

    void doReporting() {

        long now = System.currentTimeMillis();

        // check how much time has passed since last report
        if (now - lastReportTime < 2000) {
            return;
        }

        reportDataRate(now);
        this.numCutoffValsLabel.setText(String.valueOf(control.cuttOffRate));
        this.effRewardRateLabel.setText(String.valueOf(control.effRewardRate));
        // trigger table update on current mode
        ewmaDataModel.fireTableDataChanged();

        lastReportTime = now;

    }

} // class