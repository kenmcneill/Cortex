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

    private byte[] buf = new byte[500]; // enough for 8 channels
    DatagramPacket packet_REC = new DatagramPacket(buf, buf.length);
    DatagramPacket packet_SND;
    String rawString = null;
    byte[] rawData = null;

    private float cutoffPower = 40;

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
    int minNumConsecQualifiers = 5;

    protected static final int STOPPED = 0;
    protected static final int RUNNING = 1;
    protected static final int TIMED_OUT = 2;

    static Logger logger;
    static File dataFile;

    boolean running = false;
    int recordCount;

    private boolean wasTimedOut;

    // begin at 60% time over threshold
    int targetRewardRate = 60;

    private int rewardCount = 0;

    private int currNumConsecQualifiers = 0;

    int numCuttOffVals;

    private float rewardAggregate;

    private ControlGUI gui;

    int[] inhibitChannels = new int[] { -1, -1 };
    int[] inhibitBands = new int[] { 0, 0 };
    int[] inhibitRates = new int[] { 30, 30 };

    public enum RunMode {
        PreBaseline(), Feedback, PostBaseline;
    }

    RunMode runMode = RunMode.PreBaseline;
    int[][] cuttOffCounts;

    private long sessionStartTime =0;
    private long rpStartTime = 0;
    private long qualifyStartTime = 0;
    private float lastReward = 0;

    private long qualifyDuration = 500;
    long rpDuration = 500;

    static {

        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %2$s(): %5$s%6$s%n");
        logger = Logger.getLogger("cortex");
        logger.setLevel(Level.INFO);

    }

    Control(ControlGUI g) {

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                shutdown();
            }
        });

        gui = g;
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

        sessionStartTime = System.currentTimeMillis();

        rpStartTime = 0;
        qualifyStartTime = 0;
        lastReward = 0;

        bandValues = new float[numChannels][NUM_BANDS];
        bandEWMAs = new float[numChannels][NUM_BANDS];
        bandAggrs = new float[numChannels][NUM_BANDS];
        bandMeans = new float[numChannels][NUM_BANDS]; // do this in doCalcMeans()?

        cuttOffCounts = new int[numChannels][NUM_BANDS];

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

        calcMeans();

        // tell the GUI that we have fully stopped
        gui.stopped();
    }

    void calcMeans() {

        float val;

        for (int c = 0; c < numChannels; c++) {

            for (int b = DELTA_IDX; b <= GAMMA_IDX; b++) {

                val = bandAggrs[c][b] / recordCount;

                bandMeans[c][b] = Math.round(val * ROUND_FACTOR) / 100f;
            }
        }
        // set a reference into mean values
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
            calcFeedback();
        }

        gui.doReporting();

    }

    void saveData(File file) {

        FileWriter fileWriter = null;

        try {
            fileWriter = new FileWriter(file);
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Dataset,Channel,Delta,Theta,Alpha,Beta,Gamma");
        sb.append("\n");

        appendValues("PreBaseline", preBaselineMeans, sb);

        appendValues("Feedback",fBMeans, sb);

        appendValues("PostBaseline", postBaselineMeans, sb);

        try {
            fileWriter.write(sb.toString());
            fileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void appendValues(String type, float[][] table, StringBuilder sb) {

        if (table == null) {
            return;
        }

        for (int c = 0; c < numChannels; c++) {

            sb.append(type);
            sb.append(",");
            sb.append(String.valueOf(c+1));
            sb.append(",");

            for (int b = DELTA_IDX; b <= GAMMA_IDX; b++) {

                sb.append(table[c][b]);
                
                if (b < GAMMA_IDX) {
                    sb.append(",");
                }
            }
            sb.append("\n");
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

            // target matches
            if (channel == targetChannel && band == targetBand) {

                return getTargetCoeff(value) > 0;

            }
            // differential case
        } else {
            if (channel == targetChannel && band == targetBand || channel == refChannel && band == refBand) {

                return getDifferentialCoeff() > 0;
            }
        }
        // now check for inhibit
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
    void calcFeedback() {

        long now = System.currentTimeMillis();

        // are we in RP?
        if (rpStartTime > 0) {

            // still in RP? If so, resend previous reward
            if (now - rpStartTime < rpDuration) {
                this.sendFeedback(lastReward);
                return;
            }
            // RP has now elapsed
            rpStartTime = 0;
            qualifyStartTime = 0;
        }

        // get the magic number
        float coeff = getTargetCoeff();

        // check the sign and inhibit conditions
        boolean qualify = coeff > 0 && doesInhibitQualify();

        if (!qualify) {

            // reset stat
            currNumConsecQualifiers = 0;

            // reset clock on sustained qualifying
            qualifyStartTime = 0;

            // send negative feedback
            sendFeedback(Math.max(coeff, -1f));
            return;

        }

        // increment stat
        currNumConsecQualifiers++;

        // start the clock
        if (qualifyStartTime == 0) {
            qualifyStartTime = now;
            return;
        }

        // wait longer
        if (now - qualifyStartTime < qualifyDuration) {
            return;
        }

        // qualifying sustain time satisfied
        rewardCount++;

        // start RP clock
        rpStartTime = now;

        // throttle the fb to maximum of 100%
        float fb = Math.min(coeff, 1f);

        // send the value over UDP
        sendFeedback(fb);

        // during RP, lastReward will be sent
        lastReward = fb;

        // keep track of reward quantity
        rewardAggregate += fb;

    }

    /**
     * Deprecated way of rewarding sustained feedback
     * 
     * @param coeff
     * @return
     */
    float applyBonus(float coeff) {

        // maximum bonus differential
        int bonusDiff = Math.min(currNumConsecQualifiers - minNumConsecQualifiers, MAX_BONUS_DIFF);

        // % bonus for every consecutive pass over minimum
        float bonus = 1 + (bonusDiff * BONUS);

        float fb = coeff * bonus;

        return fb;
    }

    int getRewardRate() {

        long elapsed = (System.currentTimeMillis() - sessionStartTime)/(qualifyDuration + rpDuration);
        
        return elapsed < 1 ? 0 : (int) ((rewardCount / (float)elapsed) * ROUND_FACTOR);

    }

    int getRewardAmt() {

        return (int) (rewardAggregate * ROUND_FACTOR / (float) rewardCount);

    }

    float getCuttoffRate() {

        float f = numCuttOffVals / (float) (recordCount * numChannels * Control.NUM_BANDS);

        return Math.round(f * 1000) / 10f;
    }

    void sendFeedback(float coeff) {

        // convert to percent and truncate
        int fb = Math.round(coeff * ROUND_FACTOR);

        // set the reward
        packet_SND.setData(new byte[] { (byte) fb });

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
            return;

        } catch (SocketException e1) {
            e1.printStackTrace();
        }
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

        // number of channels
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

    /**
     * EWMA(t) = a * x(t) + (1-a) * EWMA(t-1)
     */
    void processData() {

        recordCount++; // one more record

        for (int c = 0; c < numChannels; c++) {

            float value;

            for (int b = DELTA_IDX; b <= GAMMA_IDX; b++) {

                value = bandValues[c][b];

                // reject out of range value in microVolts^2/Hz
                // band ewma will remain unchanged and aggregate needs current mean added
                if (value > cutoffPower) {

                    numCuttOffVals++;
                    cuttOffCounts[c][b]++;
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

    public long getQualifyDuration() {
        return qualifyDuration;
    }

    public void setQualifyDuration(long qd) {
        
        this.qualifyDuration = qd;
    }
    
    public long getRPDuration() {
        return rpDuration;
    }

    public void setRPDuration(long rpd) {
        
        this.rpDuration = rpd;
    }

} // class