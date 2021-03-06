import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.audio.AudioNode;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.ui.Picture;

public abstract class FBParadigm extends SimpleApplication {

  private static final String COLOR = "Color";

  private static float A = .75f;
  private static float ONEMINUS_A = 1f - A;

  static Logger logger = Logger.getLogger("cortex");

  private static InetAddress inet;
  private static final int PORT = 54321;
  boolean testMode = false;
  private static DatagramSocket socket;
  private byte[] buf = new byte[1];
  DatagramPacket packet = new DatagramPacket(buf, buf.length);
  String rawString = null;
  byte[] rawData = null;
  float reward = 0;
  protected float rewardEMWA = 0;
  
  float smoothChangeValue;
  private float smoothChangeTarget;

  boolean started = false;
  private Geometry geo;
  private Random random = new Random();

  int timeouts = 0;

  private BitmapText hudText;
  private boolean sync;

  AudioNode audioNode;
  boolean invertVol = false;

  static {
    try {
      System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %2$s(): %5$s%6$s%n");
      logger = Logger.getLogger("cortex");
      logger.setLevel(Level.INFO);
      inet = InetAddress.getByAddress(new byte[] { 0, 0, 0, 0 });
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
  }

  void setEWMASmoothing(float s) {

    if (s > 1 || s < .1) {
      logger.warning("Smoothing must > .1 and < 1");
      return;
    }

    A = s;
    ONEMINUS_A = 1f - A;
  }

  FBParadigm() {

    // super();

    super(new AppState[] {});

    this.setShowSettings(false);
    this.setDisplayStatView(false);
    this.setDisplayFps(false);

    AppSettings settings = new AppSettings(true);

    // settings.put("Fullscreen", true);

    settings.put("Width", 1080);

    settings.put("Height", 800);

    settings.put("Cortex", "Neurofeedback");

    this.setSettings(settings);

    this.initUDP();

    addShutdownHook();
  }

  private void addShutdownHook() {

    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {

        if (socket != null) {
          socket.close();
        }
        FBParadigm.this.stop();
      }
    });
  }

  @Override
  public void simpleInitApp() {

    assetManager.registerLocator(".", FileLocator.class);

    initParadigm();
    initHUDText();
    initRewardBar();
    inputManager.addMapping("Test Mode", new KeyTrigger(KeyInput.KEY_T));
    inputManager.addListener(actionListener, new String[] { "Test Mode" });
    
    inputManager.addMapping("Reset", new KeyTrigger(KeyInput.KEY_R));
    inputManager.addListener(actionListener, new String[] { "Reset" });

  }

  private ActionListener actionListener = new ActionListener() {
    
    public void onAction(String name, boolean keyPressed, float tpf) {

      if (!keyPressed && name.equals("Test Mode")) {
        testMode = !testMode;
      } else if (name.equals("Reset")) {

        if (started) {
          return;
        }
        reset();
      }
    }
  };

  private void reset() {
    
    audioNode.setVolume(.01f);
    reward = 0;
    smoothChangeValue = 0;
    updateRewardBar();
    resetParadigm();

  }

  void initHUDText() {

    hudText = new BitmapText(guiFont, false);
    hudText.setSize(28); // font size
    hudText.setColor(ColorRGBA.Blue); // font color
    hudText.setText("Waiting for feedback to begin..."); // the text
    hudText.setLocalTranslation(settings.getWidth() / 3f, settings.getHeight() - 200, 0); // position
    guiNode.attachChild(hudText);
  }

  void initRewardBar() {

    Quad quad = new Quad(30, 80);
    geo = new Geometry("OurQuad", quad);
    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor(COLOR, ColorRGBA.Gray);
    geo.setMaterial(mat);
    geo.setLocalTranslation(20, settings.getHeight() - 150, 0);
    guiNode.attachChild(geo);

    quad = new Quad(30, 40);
    geo = new Geometry("OurQuad", quad);
    mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor(COLOR, ColorRGBA.Green);
    geo.setMaterial(mat);
    geo.setLocalTranslation(20, settings.getHeight() - 150, 0);
    guiNode.attachChild(geo);
  }

  abstract void initParadigm();

  abstract void startParadigm();

  abstract void updateParadigm(boolean sync);

  abstract void stopParadigm();

  abstract void resetParadigm();

  private void updateRewardBar() {

    geo.setLocalScale(1, 1f+smoothChangeValue, 1);
    ColorRGBA c = smoothChangeValue < .05f ? ColorRGBA.Yellow : ColorRGBA.Green;
    geo.getMaterial().setColor(COLOR, c);  

  }

  @Override
  public void simpleUpdate(float tpf) {

    if (!testMode) {

      // waiting...
      if (!receiveUDP()) {

        timeouts++;

        if (started && timeouts > 3) {
          System.out.println("stopping...");
          stopParadigm();
          finish();
          started = false;
          timeouts = 0;
        }

        return;
      }
    }

    // get started!
    if (!started) {
      hudText.removeFromParent();
      getReward();
      smoothChangeTarget = reward;
      smoothChangeValue = reward;
      startParadigm();
      started = true;
      return;
    }

    getReward();
    calcEWMA();

    if (smoothChangeValue == smoothChangeTarget) {

      smoothChangeTarget = reward;
      sync = true;
    }

    smoothChangeValue = FBParadigm.updateIncrementalValue(smoothChangeValue, smoothChangeTarget, .01f);

    float volTarget = invertVol ? 1 - smoothChangeValue : smoothChangeValue;

    audioNode.setVolume(Math.max(volTarget, .01f));
    updateRewardBar();
    updateParadigm(sync);    

    sync = false;
  }

  void finish() {
    
    hudText.setText("Congratulations, you have completed the feedback session!");
    rootNode.attachChild(hudText);
  }

  private int calcTestReward() {

    // random * STD + MEAN
    int i = Math.round((float) random.nextGaussian() * 40f + 40f);

    return Math.max(Math.min(100, i), -100);

  }

  private void getReward() {

    int i = 0;

    if (testMode) {
      i = calcTestReward();
    } else {
      i = packet.getData()[0];
    }

    // get -1f to 1f
    reward = .1f * Math.round(i / 10f);

  }

  private void calcEWMA() {

    rewardEMWA = .01f * Math.round(100 * ((A * reward) + (ONEMINUS_A * rewardEMWA)));

  }

  void setBackground(String assetPath, String name) {

    Picture pic = new Picture(name);

    pic.setImage(assetManager, assetPath, false);

    pic.setWidth(settings.getWidth());

    pic.setHeight(settings.getHeight());

    pic.setPosition(0, 0);

    ViewPort pv = renderManager.createPreView("backgroundPV", cam);

    pv.setClearFlags(true, true, true);

    pv.attachScene(pic);

    viewPort.setClearFlags(false, true, true);

    pic.updateGeometricState();
  }

  static float updateIncrementalValue(float value, float targetVal, float incOrDec) {

    // no increment/decrement to do
    if (Math.abs(value - targetVal) < incOrDec) {
      return targetVal;
    }

    return (value < targetVal) ? value + incOrDec : value - incOrDec;
  }

  void initUDP() {

    try {
      socket = new DatagramSocket(PORT, inet);
      socket.setSoTimeout(20);

    } catch (SocketException e1) {
      e1.printStackTrace();
    }

  }

  boolean receiveUDP() {

    try {
      socket.receive(packet);
      timeouts = 0;
      return true;
    } catch (SocketTimeoutException e) {
      return false;

    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

}