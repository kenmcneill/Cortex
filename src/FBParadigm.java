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
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.ui.Picture;

public class FBParadigm extends SimpleApplication {

  private static final int UPDATE_INTERVAL = 200;
  private static final float _100F = 100f;
  private static final String COLOR = "Color";

  static final float A = .85f;
  static final float ONEMINUS_A = 1f - A;

  static Logger logger = Logger.getLogger("cortex");

  private static InetAddress inet;
  private static final int PORT = 54321;
  private static final boolean TEST = true;
  private static DatagramSocket socket;
  private byte[] buf = new byte[10];
  DatagramPacket packet = new DatagramPacket(buf, buf.length);
  String rawString = null;
  byte[] rawData = null;
  float reward = .1f;
  boolean first = true;
  private Geometry geo;
  private Random random = new Random();
  protected float rewardEMWA = 0;
  private long lastUpdateTime = 0;

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

  public static void main(String[] args) throws Exception {

    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {

        if (socket != null) {
          socket.close();
        }
      }
    });

    RocketParadigm app = new RocketParadigm();

    app.setShowSettings(false);

    AppSettings settings = new AppSettings(true);

    settings.put("Width", 1024);

    settings.put("Height", 800);

    settings.put("Cortex", "Neurofeedback");

    app.setSettings(settings);

    app.start();

  }

  FBParadigm() {

    super(new AppState[] {});

    initUDP();
  }

  @Override
  public void simpleInitApp() {

    logger.info("");

    assetManager.registerLocator(".", FileLocator.class);
    setBackground("assets/FunkyBackground.png");
    initHUDText();
    initRewardBar();
  }

  void initHUDText() {

    BitmapText hudText = new BitmapText(guiFont, false);
    // hudText.setSize(guiFont.getCharSet().getRenderedSize()); // font size
    hudText.setSize(28); // font size
    hudText.setColor(ColorRGBA.Blue); // font color
    hudText.setText("Waiting for control..."); // the text
    hudText.setLocalTranslation(settings.getWidth() / 3f, settings.getHeight() - 50, 0); // position
    guiNode.attachChild(hudText);
  }

  void initRewardBar() {

    Quad quad = new Quad(20, 50);
    geo = new Geometry("OurQuad", quad);
    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor(COLOR, ColorRGBA.Gray);
    geo.setMaterial(mat);
    geo.setLocalTranslation(20, settings.getHeight() - 100, 0);
    guiNode.attachChild(geo);

    quad = new Quad(20, 25);
    geo = new Geometry("OurQuad", quad);
    mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor(COLOR, ColorRGBA.Green);
    geo.setMaterial(mat);
    geo.setLocalTranslation(20, settings.getHeight() - 100, 0);
    guiNode.attachChild(geo);
  }

  void initParadigm() {

  }

  void updateParadigm() {

  }

  void updateRewardBar() {

    geo.setLocalScale(1, 1 + rewardEMWA, 1);

    ColorRGBA c = rewardEMWA > 0 ? ColorRGBA.Green : ColorRGBA.Yellow;

    geo.getMaterial().setColor(COLOR, c);
  }

  @Override
  public void simpleUpdate(float tpf) {

    if (!TEST) {

      // waiting...
      if (!receiveUDP()) {
        return;
      }
    }

    // get started!
    if (first) {
      initParadigm();
      getReward();
      rewardEMWA = reward;
      first = false;
      return;
    }

    getReward();
    calcEWMA();

    long now = System.currentTimeMillis();
    
    // 
    if (now - lastUpdateTime < UPDATE_INTERVAL) {
      return;
    }
    lastUpdateTime = now;

    // System.out.println(this.rewardEMWA);
    updateParadigm();

  }

  private float calcRewardTest() {

    // random * STD + MEAN
    int i = Math.round((float) random.nextGaussian() * 30 + 30f);

    return i / 100f;

  }

  void getReward() {

    if (TEST) {
      reward = calcRewardTest();
      return;
    }
    String s = new String(packet.getData());
    reward = Integer.valueOf(s) / _100F;

  }

  void calcEWMA() {

    rewardEMWA = .1f * Math.round(10 * ((A * reward) + (ONEMINUS_A * rewardEMWA)));
  }

  void setBackground(String assetPath) {

    Picture pic = new Picture("backgroundPic");

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

  void initUDP() {

    logger.info("");

    try {
      socket = new DatagramSocket(PORT, inet);
      socket.setSoTimeout(200);

    } catch (SocketException e1) {
      e1.printStackTrace();
    }

  }

  boolean receiveUDP() {

    try {
      socket.receive(packet);
      return true;
    } catch (SocketTimeoutException e) {
      logger.warning("Socket timed out.");
      return false;

    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

}