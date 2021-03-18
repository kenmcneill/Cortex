import java.awt.Color;
import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

class ChannelBandDataModel extends AbstractTableModel {

    /**
     *
     */
    final Control control;

    /**
     * @param controlGUI
     */
    ChannelBandDataModel(Control controlGUI) {
        this.control = controlGUI;
    }

    static class EWMADataModel extends ChannelBandDataModel {

        EWMADataModel(Control ctrl) {
            super(ctrl);
        }

        private static final long serialVersionUID = 1L;

        @Override
        public Float getValueAt(int rowIndex, int columnIndex) {

            return Math.round(control.bandEWMAs[rowIndex][columnIndex] * 100) / 100f;
        }

    }

    static class PreBaselineMeanDataModel extends ChannelBandDataModel {

        PreBaselineMeanDataModel(Control ctrl) {
            super(ctrl);
        }

        private static final long serialVersionUID = 1L;

        @Override
        public Float getValueAt(int rowIndex, int columnIndex) {
            return control.preBaselineMeans[rowIndex][columnIndex];
        }
    }

    static class FeedbackMeanDataModel extends ChannelBandDataModel {

        FeedbackMeanDataModel(Control ctrl) {
            super(ctrl);
        }

        private static final long serialVersionUID = 1L;

        @Override
        public Float getValueAt(int rowIndex, int columnIndex) {
            return control.fBMeans[rowIndex][columnIndex];
        }
    }

    static class PostBaselineMeanDataModel extends ChannelBandDataModel {

        PostBaselineMeanDataModel(Control ctrl) {
            super(ctrl);
        }

        private static final long serialVersionUID = 1L;

        @Override
        public Float getValueAt(int rowIndex, int columnIndex) {
            return control.postBaselineMeans[rowIndex][columnIndex];
        }
    }

    static class ColorRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        private Control control;

        private int[][] lastCuttoffCounts;

        ColorRenderer(Control c) {
            control = c;
            lastCuttoffCounts = new int[control.getNumChannels()][Control.NUM_BANDS];
        }

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

            int currNum = control.cuttOffCounts[row][column];
            int lastNum = lastCuttoffCounts[row][column];
            int delta = currNum - lastNum;

            lastCuttoffCounts[row][column] = currNum;

            if (delta > 0) {
                rend.setBackground(Color.RED);
                return rend;
            }

            if (!control.runMode.equals(Control.RunMode.Feedback)) {
                rend.setBackground(Color.WHITE);
                return rend;                
            }

            float fvalue = (Float) value;

            boolean qual = control.doesValueQualify(fvalue, row, column);

            if (qual) {
                rend.setBackground(Color.GREEN);
                return rend;
            }

            rend.setBackground(Color.WHITE);
            return rend;
        }

    }

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    @Override
    public String getColumnName(int column) {

        return ControlGUI.BAND_NAMES[column];
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