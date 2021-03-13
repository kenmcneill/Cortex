import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** 
 * 
 */
public class Control {

    private static final int MAX_BONUS_DIFF = 20;
    private static final float BONUS = .01f;
    private static final int ROUND_FACTOR = 100;
    private static final float _50F = 50f;
    static final int NUM_BANDS = 5;
    private static final int TIMEOUT = 1000;
    private DatagramSocket socket_REC;
    private DatagramSocket socket_SND;

    private static final byte[] LOCAL_HOST = new byte[] { 127, 0, 0, 1 };
    private static final byte[] THIS_HOST_WILDCARD = new byte[] { 0, 0, 0, 0 };
    private static final byte[] DATA_HOST = THIS_HOST_WILDCARD;

    private InetAddress listenAddress;
    private int listenPort = 12345;

    byte[] fbSendHost = LOCAL_HOST;
    private InetAddress fbSendAddress;
    int fbSendPort = 54321;

    private boolean listening;
    private byte[] buf = new byte[500]; // enough for 8 channels
    DatagramPacket packet_REC = new DatagramPacket(buf, buf.length);
    DatagramPacket packet_SND;
    String rawString = null;
    byte[] rawData = null;

    private float cutoffPower = 50;

    final Pattern pattern = Pattern.compile("\\],\\[|\\[|\\]|,");

    float[][] bandValues;
    float[][] bandEWMAs;
    float[][] bandAggrs;
    float[][] bandMeans;
    float[][] preBaselineMeans;
    float[][] fBMeans;
    float[][] postBaselineMeans;

    static final float A = .85f;
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
    private int numChannels = 8;
    int minNumConsecQualifiers = 10;

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

    private float rewardAggregate;

    private ControlGUI gui;

    int[] inhibitChannels = new int[] { -1, -1 };
    int[] inhibitBands = new int[] { 0, 0 };
    int[] inhibitRates = new int[] { 30, 30 };

    public enum RunMode {
        PreBaseline(), Feedback, PostBaseline;
    }

    RunMode runMode = RunMode.PreBaseline;

    static {

        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %2$s(): %5$s%6$s%n");
        logger = Logger.getLogger("cortex");
        logger.setLevel(Level.INFO);

        // fileWriter = new FileWriter("logs/session-data-" + new Date() + ".txt");

    }

    Control(ControlGUI g) {

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                shutdown();
            }
        });

        gui = g;
        // initState();
    }

    private void initNetworking() {

        closeNetworking();

        try {

            listenAddress = InetAddress.getByAddress(DATA_HOST);
            fbSendAddress = InetAddress.getByAddress(fbSendHost);

            packet_SND = new DatagramPacket(new byte[3], 3, fbSendAddress, fbSendPort);
            socket_SND = new DatagramSocket();

            initUDPReciever();

        } catch (Exception e) {
            e.printStackTrace();
        }
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
        closeNetworking();

        if (fileWriter == null)
            return;

        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    private void closeNetworking() {

        if (socket_REC != null) {
            socket_REC.close();
        }

        if (socket_SND != null) {
            socket_SND.close();

        }
    }

    protected void setRunMode(RunMode rMode) {

        runMode = rMode;

    }

    void setRunning(boolean r) {

        running = r;

        if (running) {

            initState();
            initNetworking();
            startProcessThread();

        }
    }

    private void initState() {

        recordCount = 0;
        numCuttOffVals = 0;

        currNumConsecQualifiers = 0;
        rewardCount = 0;
        rewardAggregate = 0;

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

                    try {
                        update();
                    } catch (Exception e) {
                        e.printStackTrace();
                        running = false;
                    }

                }
                stopped();
            }

        }).start();

    }

    private void stopped() {

        closeNetworking();

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

        float val;

        for (int c = 0; c < numChannels; c++) {

            for (int b = DELTA_IDX; b < GAMMA_IDX; b++) {

                val = bandAggrs[c][b] / recordCount;

                bandMeans[c][b] = Math.round(val * ROUND_FACTOR) / 100f;
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

        // simplest case
        if (!isDifferentialMode()) {

            if (channel == targetChannel && band == targetBand) {

                return getTargetCoeff(value) > 0;

            }
            // differential case
        } else {
            if (channel == targetChannel && band == targetBand || channel == refChannel && band == refBand) {

                return getDifferentialCoeff() > 0;
            }
        }
        // inhibit case
        for (int i = 0; i < inhibitChannels.length; i++) {

            if (channel == inhibitChannels[i] && band == inhibitBands[i]) {
                return doesInhibitQualify(value, channel, band, inhibitRates[i]);
            }
        }

        return false;
    }

    private boolean isDifferentialMode() {
        return refChannel > -1;
    }

    float getTargetCoeff() {

        if (isDifferentialMode()) {
            return getDifferentialCoeff();
        }

        // get the current feedback value
        float value = Math.round(this.bandEWMAs[targetChannel][targetBand] * ROUND_FACTOR) / 100F;

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
            diff = -diff;
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

        // baseline ration of target to reference
        float baseRatio = baseMean1 / baseMean2;

        // get the current feedback value
        float value1 = bandEWMAs[targetChannel][targetBand];

        // get the current feedback value
        float value2 = bandEWMAs[refChannel][targetBand];

        // corresponding feedback ratio
        float valueRatio = value1 / value2;

        float rewardCoeff = _50F / targetRewardRate;

        // find the threshold value for the ratio (rewardCoeff could be negative)
        float threshold = baseRatio * Math.abs(rewardCoeff);

        // calc the reward differential (negative if value less than threshold)
        float rewardDiff = (float) Math.log(valueRatio / threshold);

        // if coeff is negative, the ratio is being supressed, so reward the negative
        // diff
        if (rewardCoeff < 0) {
            rewardDiff = -rewardDiff;
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
        boolean qualify = (coeff > 0 && doesInhibitQualify());

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

        // count this reward
        this.rewardCount++;

        // maximum bonus differential
        int bonusDiff = Math.min(currNumConsecQualifiers - minNumConsecQualifiers, MAX_BONUS_DIFF);

        // % bonus for every consecutive pass over minimum
        float bonus = 1 + (bonusDiff * BONUS);

        float fb = coeff * bonus;

        // maximum 100%
        fb = Math.min(fb, 1f);

        // send the value over UDP
        doSendFeedback(fb);

        // keep track of
        rewardAggregate += fb;

    }

    int getRewardRate() {

        return (int) (rewardCount / ((float) recordCount) * ROUND_FACTOR);

    }

    int getRewardAmt() {

        return (int) (rewardAggregate * ROUND_FACTOR / (float) rewardCount);

    }

    int getCuttoffRate() {

        return (int) (ROUND_FACTOR * numCuttOffVals / (float) (recordCount * numChannels * Control.NUM_BANDS));
    }

    void doSendFeedback(float coeff) {

        // convert to percent and truncate
        int fb = Math.round(coeff * ROUND_FACTOR);

        // set the reward
        packet_SND.setData(String.valueOf(fb).getBytes());

        try {
            socket_SND.send(packet_SND);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void initUDPReciever() {

        try {
            socket_REC = new DatagramSocket(listenPort, listenAddress);
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

        recordCount++; // one more record

        for (int c = 0; c < numChannels; c++) {

            float value;

            for (int b = DELTA_IDX; b < GAMMA_IDX; b++) {

                value = bandValues[c][b];

                // reject out of range value in microVolts^2/Hz
                // band ewma will remain unchanged and aggregate needs current mean added
                if (value > cutoffPower) {

                    numCuttOffVals++;

                    bandAggrs[c][b] += (bandAggrs[c][b] / recordCount - 1);
                    continue;
                }

                // very first method invocation, set avg to value
                if (first) {
                    bandEWMAs[c][b] = value;

                } else {
                    bandEWMAs[c][b] = (A * value) + (ONEMINUS_A * bandEWMAs[c][b]);

                }
                // get a pure mean aggregate
                bandAggrs[c][b] += value;
            }
        }
        if (first) {
            first = false;
        }
    } // method

} // class