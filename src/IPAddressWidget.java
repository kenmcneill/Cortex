import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Vector;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicSpinnerUI;

class IPAddressWidget extends JPanel {

    /**
     *
     */
    private final ControlGUI controlGUI;

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    Vector<JSpinner> spinners = new Vector<JSpinner>(4);

    IPAddressWidget(ControlGUI controlGUI, byte[] address) {

        this.controlGUI = controlGUI;
        FlowLayout flow = (FlowLayout) this.getLayout();
        flow.setAlignment(FlowLayout.LEFT);
        flow.setHgap(1);
        flow.setAlignOnBaseline(true);

        JSpinner spinner;
        JLabel period;

        for (int i = 0; i < 4; i++) {

            spinner = new JSpinner(new SpinnerNumberModel(this.controlGUI.control.fbSendHost[i], 0, 254, 1));
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

    @Override
    public void setEnabled(boolean enabled) {

        for (JSpinner spin : spinners) {
            spin.setEnabled(enabled);
        }

    }
    
    static class PortWidget extends JSpinner {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

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

            ((NumberEditor) getEditor()).getFormat().setGroupingUsed(false);

            Dimension d = getPreferredSize();
            d.setSize(d.width - 25, d.getHeight());
            setPreferredSize(d);
        }

    }
}