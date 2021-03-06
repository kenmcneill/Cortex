import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** TODO:javadoc */
public class Control {

    private static final float ROUNDF = 100f;
    private static final float _50F = 50f;
    static final int NUM_BANDS = 5;
    private static final int TIMEOUT = 1000;
    private DatagramSocket socket_REC;
    private DatagramSocket socket_SND;

    // 192.168.1.112
    private static InetAddress RECEIVE_ADDRESS;
    private static int RECEIVE_PORT = 12345;

    private static final byte[] LOCAL_HOST = new byte[] { 127, 0, 0, 1 };
    private static final byte[] THIS_HOST_WILDCARD = new byte[] { 0, 0, 0, 0 };
    private static final byte[] DATA_HOST = THIS_HOST_WILDCARD;
    private static final byte[] FEEDBACK_HOST = LOCAL_HOST;

    private InetAddress sendAddress;

    private int SEND_PORT = 54321;

    private boolean listening;
    private byte[] buf = new byte[500]; // enough for 8 channels
    DatagramPacket packet_REC = new DatagramPacket(buf, buf.length);
    DatagramPacket packet_SND;
    String rawString = null;
    byte[] rawData = null;

    private static final float CUTOFF = 335;

    final Pattern pattern = Pattern.compile("\\],\\[|\\[|\\]|,");

    float[][] bandValues;
    float[][] bandEWMAs;
    float[][] bandAggrs;
    float[][] bandMeans;
    float[][] preBaselineMeans;
    float[][] fBMeans;
    float[][] postBaselineMeans;

    static final float A = .90f;
    static final float ONEMINUS_A = 1f - A;

    final static int DELTA_IDX = 0;
    final static int THETA_IDX = 1;
    final static int ALPHA_IDX = 2;
    final static int BETA_IDX = 3;
    final static int GAMMA_IDX = 4;

    int targetBand = ALPHA_IDX;
    int targetChannel = 0;
    int refChannel = -1;
    int refBand = targetBand;

    private boolean first = true;

    private static final int PREFIX = 28;
    private static final int SUFFIX = 4;
    private int numChannels = 4;
    int minNumConsecQualifiers = 3;

    protected static final int STOPPED = 0;
    protected static final int RUNNING = 1;
    protected static final int TIMED_OUT = 2;

    private StringBuilder sb = new StringBuilder(50);

    static Logger logger;
    static File dataFile;
    static FileWriter fileWriter;

    boolean running = false;
    int recordCount;

    private boolean wasTimedOut;

    // begin at 60% time over threshold
    int targetRewardRate = 60;

    private int rewardCount = 0;

    private int currNumConsecQualifiers = 0;

    private int numCuttOffVals;

    private ControlGUI gui;

    int[] inhibitChannels = new int[] { -1, -1 };
    int[] inhibitBands = new int[] { 0, 0 };
    int[] inhibitRates = new int[] { 30, 30 };

    public enum RunMode {
        PreBaseline(), Feedback, PostBaseline;
    }

    RunMode runMode = RunMode.PreBaseline;
    int effRewardRate;
    int cuttOffRate;

    static {

        // Handler fh;

        try {

            // fh = new FileHandler("logs/cortex.txt");
            // fh.setFormatter(logger.getParent().getHandlers()[0].getFormatter());
            // logger.addHandler(fh);
            System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %2$s(): %5$s%6$s%n");
            // System.setProperty("jdk.gtk.version", "4");
            // System.setProperty("jdk.gtk.verbose", "true");
            // UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
            // Formatter f =
            // logger.getParent().getHandlers()[0].getFormatter().format(record);
            // logger.getParent().getHandlers()[0].setFormatter(f);
            logger = Logger.getLogger("cortex");
            logger.setLevel(Level.INFO);

            RECEIVE_ADDRESS = InetAddress.getByAddress(DATA_HOST);
            // SEND_ADDRESS = InetAddress.getByAddress(FEEDBACK_HOST);

            // fileWriter = new FileWriter("logs/session-data-" + new Date() + ".txt");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    Control(ControlGUI g) {

        try {
            sendAddress = InetAddress.getByAddress(FEEDBACK_HOST);
            packet_SND = new DatagramPacket(new byte[3], 3, sendAddress, SEND_PORT);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        gui = g;

        initState();
    }

    public int getNumChannels() {
        return numChannels;
    }

    public void setNumChannels(int value) {

        if (running) {
            setRunning(false);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        numChannels = value;
        initState();
	}

    void shutdown() {
        running = false;

        if (socket_REC != null) {
            socket_REC.close();
        }

        if (socket_SND != null) {
            socket_SND.close();
        }
        if (fileWriter == null)
            return;

        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    protected void setRunMode(RunMode rMode) {

        if (rMode.equals(RunMode.Feedback) && socket_SND == null) {
            try {
                socket_SND = new DatagramSocket();
            } catch (SocketException e) {
                e.printStackTrace();
                // can't enter feedback mode
                return;
            }
        }
        runMode = rMode;

    }

    void initApp() {

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                shutdown();
            }
        });

        initUDPReciever();

        if (!listening) {
            return;
        }

    }

    void setRunning(boolean r) {

        running = r;

        if (running) {

            initState();
            startProcessThread();
        }
    }

    private void initState() {

        recordCount = 0;
        effRewardRate = 0;

        numCuttOffVals = 0;
        cuttOffRate = 0;

        bandValues = new float[numChannels][NUM_BANDS];
        bandEWMAs = new float[numChannels][NUM_BANDS];
        bandAggrs = new float[numChannels][NUM_BANDS];
        bandMeans = new float[numChannels][NUM_BANDS]; // do this in doCalcMeans()?

        switch (runMode) {

            case PreBaseline:
                preBaselineMeans = new float[numChannels][NUM_BANDS];
                break;
            case Feedback:
                fBMeans = new float[numChannels][NUM_BANDS];
                break;
            case PostBaseline:
                postBaselineMeans = new float[numChannels][NUM_BANDS];
                break;
        }

    }

    private void startProcessThread() {

        new Thread(new Runnable() {

            @Override
            public void run() {

                while (running) {
                    update();
                }
                stopped();
            }

        }).start();

    }

    private void stopped() {

        if (recordCount == 0) {
            logger.warning("No data was received.");
            gui.stopped();
            return;
        }

        doCalcMeans();

        // save a reference to the baseline values
        switch (runMode) {

            case PreBaseline:
                preBaselineMeans = bandMeans;
                break;
            case Feedback:
                fBMeans = bandMeans;
                break;
            case PostBaseline:
                postBaselineMeans = bandMeans;
                break;
        }

        // tell the GUI that we have fully stopped
        gui.stopped();
    }

    private void doCalcMeans() {

        for (int c = 0; c < numChannels; c++) {

            for (int b = DELTA_IDX; b < GAMMA_IDX; b++) {
                bandMeans[c][b] = bandAggrs[c][b] / recordCount;
                bandMeans[c][b] = Math.round(bandMeans[c][b] * ROUNDF) / ROUNDF;
            }
        }
    }

    private void update() {

        while (running && !receiveUDP()) {

            wasTimedOut = true;
            gui.setRunStatus(TIMED_OUT);

            try {
                Thread.sleep(TIMEOUT);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // finished, stop; last incoming packet is ignored.
        if (!running) {
            return;
        }

        if (wasTimedOut) {
            wasTimedOut = false;
            gui.setRunStatus(RUNNING);
        }

        parseData();
        processData();

        // do reward first to attentuate feedback delay
        if (runMode.equals(RunMode.Feedback)) {
            doFeedback();
        }

        gui.doReporting();

    }

    void reportValues(long now) {

        // reuse builder
        sb.delete(0, sb.length());

        sb.append(now);

        for (int b = DELTA_IDX; b < GAMMA_IDX; b++) {

            sb.append(',');
            sb.append(bandEWMAs[b]);

        }
        sb.append("\n");
        try {
            // fileWriter.write(sb.toString());
            System.out.println(sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    boolean doesInhibitQualify() {

        if (inhibitChannels[0] == -1) {
            return true;
        }

        int chan;
        int ban;
        int rate;

        for (int i = 0; i < inhibitChannels.length; i++) {

            // get each channel
            chan = inhibitChannels[i];

            if (chan == -1) {
                break;
            }

            // get each corresponding band
            ban = inhibitBands[i];
            rate = inhibitRates[i];

            // get the current feedback value
            float value = this.bandEWMAs[chan][ban];

            if (!doesInhibitQualify(value, chan, ban, rate)) {
                return false;
            }

        }
        return true;

    }

    boolean doesInhibitQualify(float value, int channel, int band, int rate) {

        // get the baseline mean power
        float baseMean = preBaselineMeans[channel][band];

        // find the effective threshold power value
        float threshold = baseMean * (50f / rate);

        if (value > threshold) {
            return false;
        }

        return true;
    }

    boolean doesValueQualify(float value, int channel, int band) {

        if (channel == targetChannel && band == targetBand) {
            return getTargetCoeff(value) > 0;
        }

        for (int i = 0; i < inhibitChannels.length; i++) {

            if (channel == inhibitChannels[i] && band == inhibitBands[i]) {
                return doesInhibitQualify(value, channel, band, inhibitRates[i]);
            }
        }

        return false;
    }

    float getTargetCoeff() {

        if (refChannel > -1) {
            return getDifferentialCoeff();
        }

        // get the current feedback value
        float value = Math.round(this.bandEWMAs[targetChannel][targetBand] * ROUNDF) / ROUNDF;

        return getTargetCoeff(value);

    }

    float getTargetCoeff(float value) {

        // get the baseline mean power
        float baseMean = preBaselineMeans[targetChannel][targetBand];

        float rewardCoeff = _50F / targetRewardRate;

        // find the effective threshold power value
        float threshold = baseMean * Math.abs(rewardCoeff);

        // calculate an approx. % differential in magnitude and direction +/-
        float diff = (float) Math.log(value / threshold);

        // invert sign if reward is proportional to suppression
        if (rewardCoeff < 0) {
            diff -= diff;
        }
        return diff;
    }

    /**
     * 
     */

    float getDifferentialCoeff() {

        // get the target baseline mean power
        float baseMean1 = preBaselineMeans[targetChannel][targetBand];

        // get the reference baseline mean power
        float baseMean2 = preBaselineMeans[refChannel][targetBand];

        float bdiff = (float) Math.log(baseMean1 / baseMean2);

        // get the current feedback value
        float value1 = bandEWMAs[targetChannel][targetBand];

        // get the current feedback value
        float value2 = bandEWMAs[refChannel][targetBand];

        float vdiff = (float) Math.log(value1 / value2);

        float rewardCoeff = _50F / targetRewardRate;

        // find the effective threshold power value
        float threshold = bdiff * Math.abs(rewardCoeff);

        float rewardDiff = (float) Math.log(vdiff / threshold);

        // if coeff is negative, the ratio is being supressed, so reward the negative
        // diff
        if (rewardCoeff < 0) {
            rewardDiff -= rewardDiff;
        }

        return rewardDiff;
    }

    /**
     * ln(x / y) = ln(x) - ln(y) ~= % change from x -> y
     * 
     */
    void doFeedback() {

        // get the magic number
        float coeff = getTargetCoeff();

        // check the sign and inhibit conditions
        boolean qualify = coeff > 0 && doesInhibitQualify();

        // check the value
        if (!qualify) {

            // NOP
            if (currNumConsecQualifiers == 0) {
                return;
            }

            // reset
            currNumConsecQualifiers = 0;

            // send new state
            doSendFeedback(coeff);
            return;

        }

        // increment
        currNumConsecQualifiers++;

        // wait for more qualifiers
        if (currNumConsecQualifiers < minNumConsecQualifiers) {
            return;
        }

        // count this reward feedback
        this.rewardCount++;
        this.effRewardRate = (int) ((float) rewardCount / ((float) recordCount) * ROUNDF);

        // 5% bonus for every consecutive pass over minimum
        float bonus = 1 + (currNumConsecQualifiers - minNumConsecQualifiers) / 5f;

        doSendFeedback(coeff * bonus);
    }

    void doSendFeedback(float coeff) {

        // convert to percent and truncate
        int fb = Math.round(coeff * ROUNDF);

        // set the reward
        packet_SND.setData(String.valueOf(fb).getBytes());

        try {
            socket_SND.send(packet_SND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void initUDPReciever() {

        try {
            socket_REC = new DatagramSocket(RECEIVE_PORT, RECEIVE_ADDRESS);
            socket_REC.setSoTimeout(200);
            listening = true;
            return;

        } catch (SocketException e1) {
            e1.printStackTrace();
        }
        listening = false;
    }

    boolean receiveUDP() {

        try {
            socket_REC.receive(packet_REC);
            return true;
        } catch (SocketTimeoutException e) {

            logger.warning("Socket timed out.");
            return false;

        } catch (IOException e) {
            socket_REC.close();
            throw new RuntimeException(e);
        }

    }

    void parseData() {

        // trim down, removing prefixes, suffixes
        rawData = Arrays.copyOfRange(packet_REC.getData(), PREFIX, packet_REC.getLength() - SUFFIX);

        ByteArrayInputStream bais = new ByteArrayInputStream(rawData);

        Scanner scanner = new Scanner(bais).useDelimiter(pattern);

        int c = 0;

        // Always 5 bands
        while (scanner.hasNext()) {

            bandValues[c][DELTA_IDX] = scanner.nextFloat();
            bandValues[c][THETA_IDX] = scanner.nextFloat();
            bandValues[c][ALPHA_IDX] = scanner.nextFloat();
            bandValues[c][BETA_IDX] = scanner.nextFloat();
            bandValues[c][GAMMA_IDX] = scanner.nextFloat();
            c++;
        }

        scanner.close();

    }

    // avg -= avg / N;
    // avg += new_sample / N;
    // EMA: oldValue + alpha * (value - oldValue);
    // EWMA:(alpha * newValue) + (1 - alpha) * prevValue;
    /**
     * EWMA(t) = a * x(t) + (1-a) * EWMA(t-1) Can also have a time window (.5s)
     */
    void processData() {

        for (int c = 0; c < numChannels; c++) {

            float value;

            for (int b = DELTA_IDX; b < GAMMA_IDX; b++) {

                value = bandValues[c][b];

                // reject out of range value in microVolts^2/Hz
                // band ewma will remain unchanged and aggregate needs current mean added
                if (value > CUTOFF) {

                    numCuttOffVals++;
                    cuttOffRate = Math.round(numCuttOffVals * ROUNDF / recordCount);

                    bandAggrs[c][b] += bandAggrs[c][b] / recordCount;
                    continue;
                }

                // very first method invocation, set avg to value
                if (first) {
                    bandEWMAs[c][b] = value;

                } else {
                    bandEWMAs[c][b] = (A * value) + ONEMINUS_A * bandEWMAs[c][b];

                }
                // get a pure mean aggregate
                bandAggrs[c][b] += value;
            }
        }
        recordCount++; // finished with this record
        if (first) {
            first = false;
        }
    } // method


} // class