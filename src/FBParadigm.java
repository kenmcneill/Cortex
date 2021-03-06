import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.logging.Logger;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AppState;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.audio.AudioData.DataType;
import com.jme3.audio.AudioNode;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh.Type;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.ui.Picture;

public class FBParadigm extends SimpleApplication {

  private Spatial rocket;
  private ParticleEmitter fire;
  private Node top;

  private static final float THRUST_COEFF = .01f;

  private AudioNode thrustAudio;

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
  long lastTimestamp = 0;
  private Geometry geo;
  private Random random = new Random();

  static {
    try {
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

    FBParadigm app = new FBParadigm();

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
    initBackground();
    initHUDText();
    initRewardBar();
  }

  void initHUDText() {
    BitmapText hudText = new BitmapText(guiFont, false);
    // hudText.setSize(guiFont.getCharSet().getRenderedSize()); // font size
    hudText.setSize(28); // font size
    hudText.setColor(ColorRGBA.Blue); // font color
    hudText.setText("Waiting for signal from control..."); // the text
    hudText.setLocalTranslation(settings.getWidth() / 3f, settings.getHeight() - 50, 0); // position
    guiNode.attachChild(hudText);
  }

  void initRewardBar() {

    Quad quad = new Quad(20, 50);
    geo = new Geometry("OurQuad", quad);
    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor("Color", ColorRGBA.Gray);
    geo.setMaterial(mat);
    geo.setLocalTranslation(20, settings.getHeight() - 100, 0);
    guiNode.attachChild(geo);

    quad = new Quad(20, 25);
    geo = new Geometry("OurQuad", quad);
    mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setColor("Color", ColorRGBA.Green);
    geo.setMaterial(mat);
    geo.setLocalTranslation(20, settings.getHeight() - 100, 0);
    guiNode.attachChild(geo);
  }

  void initRocketParadigm() {

    top = new Node();
    // top.scale(.6f);
    top.setLocalScale(.5f);
    top.setLocalTranslation(0, -3.5f, 0);

    initRocketGraphics();
    initBlastEffect();

    top.attachChild(rocket);
    top.attachChild(fire);

    rootNode.attachChild(top);

    thrustAudio = new AudioNode(assetManager, "assets/quietthrust.wav", DataType.Buffer);
    thrustAudio.setLooping(true);
    thrustAudio.setPositional(false);
    thrustAudio.setVolume(.5f);
  }

  void updateParadigm() {

    top.move(0, THRUST_COEFF * reward, 0);
    // thrustAudio.setVolume(reward);
    updateRewardBar();

  }

  void updateRewardBar() {

    int i = Math.round(reward*10);

    geo.setLocalScale(1, 1 + i/10f, 1);

    ColorRGBA c = reward > 0 ? ColorRGBA.Green : ColorRGBA.Yellow;

    geo.getMaterial().setColor("Color", c);
  }

  @Override
  public void simpleUpdate(float tpf) {

    // waiting...
    if (!receiveUDP()) {
      return;
    }

    // get started!
    if (first) {
      initRocketParadigm();
      thrustAudio.play();
      // thrustAudio2.play();
      fire.emitAllParticles();
      first = false;
      return;
    }

    calcReward();
    updateParadigm();
  }

  private void calcRewardTest() {

    // random * STD + MEAN
    int i = Math.round((float) random.nextGaussian() * 15 + 15f);

    reward = i / 100f;

    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

  }

  void calcReward() {

    if (TEST) {
      calcRewardTest();
      return;
    }

    String s = new String(packet.getData());

    reward = Integer.valueOf(s) / 100f;
  }

  void initRocketGraphics() {

    rocket = (Spatial) assetManager.loadModel("assets/10475_Rocket_Ship_v1_L3.obj");
    rocket.setLocalScale(.01f);
    rocket.setLocalTranslation(0, 0, 0);
    rocket.rotate((float) -Math.PI / 2, 0, 0f);

    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setTexture("ColorMap", assetManager.loadTexture("assets/10475_Rocket_Ship_v1_Diffuse.jpg"));

    rocket.setMaterial(mat);

  }

  void initBlastEffect() {
    fire = new ParticleEmitter("blast", Type.Triangle, 30);

    Material mat_red = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");

    mat_red.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/flame.png"));
    fire.setMaterial(mat_red);
    fire.setImagesX(2);
    fire.setImagesY(2); // 2x2 texture animation
    fire.setStartColor(new ColorRGBA(1f, .5f, .5f, 1f)); // redish
    fire.setEndColor(new ColorRGBA(1f, 1f, 1f, 0.2f)); // white
    fire.getParticleInfluencer().setInitialVelocity(new Vector3f(0, -2, 0));
    fire.setStartSize(.4f);
    fire.setEndSize(0.1f);
    fire.setGravity(0, 0, 0);
    fire.setLowLife(0.5f);
    fire.setHighLife(1.5f);
    fire.getParticleInfluencer().setVelocityVariation(0.1f);

    fire.setParticlesPerSec(20);

    rootNode.attachChild(fire);
  }

  void initBackground() {

    Picture pic = new Picture("background");

    pic.setImage(assetManager, "assets/FunkyBackground.png", false);

    pic.setWidth(settings.getWidth());

    pic.setHeight(settings.getHeight());

    pic.setPosition(0, 0);

    ViewPort pv = renderManager.createPreView("background", cam);

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

    if (TEST) {
      return true;
    }

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