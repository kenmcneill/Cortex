import com.jme3.audio.AudioData.DataType;
import com.jme3.audio.AudioNode;
import com.jme3.audio.LowPassFilter;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh.Type;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class RocketParadigm extends FBParadigm {

  private static final int LOW = 0;
  private static final int MED = 1;
  private static final int HI = 2;
  private static final float thresholdY = 4.1f;
  private float startingY = -3.7f;
  private Spatial rocket;
  private ParticleEmitter blastEffect;
  private Node rocketNode;

  private static final float THRUST_COEFF = .005f; // .005f;

  private int stage = 1;

  float low = .2f;
  float med = .6f;
  float hi = 1.2f;

  int lastLevel = 0;
  int level = 0;

  private AudioNode thrustAudio;
  private long lastChange;

  RocketParadigm() {
    super();
  }

  void initParadigm() {

    rocketNode = new Node();
    // top.scale(.6f);
    rocketNode.setLocalScale(.5f);
    rocketNode.setLocalTranslation(0, startingY, .5f);

    initRocketGraphics();
    initBlastEffect();

    DirectionalLight dLight = new DirectionalLight();
    dLight.getDirection().set(-.2f, -.5f, .9f);
    rootNode.addLight(dLight);

    rocketNode.attachChild(rocket);

    rootNode.attachChild(rocketNode);

    thrustAudio = new AudioNode(assetManager, "assets/quietthrust.wav", DataType.Buffer);

    thrustAudio.setPositional(false);
    thrustAudio.setLooping(true);
    thrustAudio.setVolume(.3f);
    thrustAudio.setReverbEnabled(true);
    thrustAudio.setDryFilter(new LowPassFilter(1, .4f));

  }

  @Override
  void startParadigm() {

    rocketNode.attachChild(blastEffect);
    audioRenderer.playSource(thrustAudio);

  }

  @Override
  void stopParadigm() {

    blastEffect.removeFromParent();
    audioRenderer.stopSource(thrustAudio);
  }

  void updateParadigm() {

    if (rocketNode.getLocalTranslation().y >= thresholdY) {

      switch (stage) {

      case 1:
        stage = 2;
        setBackground("assets/clouds.jpg", "stage 2");
        rocketNode.setLocalTranslation(0, -thresholdY, 0);
        break;
      case 2:
        stage = 3;
        setBackground("assets/earth-atmosphere.jpg", "stage 3");
        rocketNode.setLocalTranslation(0, -thresholdY, -1);
        break;
      case 3:
        stage = 4;
        setBackground("assets/earth-outerspace.jpg", "stage 4");
        rocketNode.setLocalTranslation(0, -thresholdY, -2);
      case 4:
        // finished
      }

      return;

    }

    rocketNode.move(0, THRUST_COEFF * rewardEMWA, 0);

    long now = System.currentTimeMillis();

    if (now - lastChange < 250) {
      return;
    }

    level = rewardEMWA <= low ? LOW : rewardEMWA <= med ? MED : HI;

    if (level == lastLevel) {
      return;
    }

    switch (level) {
    case LOW:
      blastEffect.setParticlesPerSec(10);
      thrustAudio.setVolume(low);
      break;

    case MED:
      blastEffect.setParticlesPerSec(20);
      thrustAudio.setVolume(med);
      break;
    case HI:
      blastEffect.setParticlesPerSec(40);
      thrustAudio.setVolume(hi);

    }
    lastLevel = level;
    lastChange = now;
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

    blastEffect = new ParticleEmitter("blast", Type.Triangle, 10);

    Material material = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");

    material.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/flame.png"));
    blastEffect.setMaterial(material);
    blastEffect.setImagesX(2);
    blastEffect.setImagesY(2); // 2x2 texture animation
    blastEffect.setStartColor(new ColorRGBA(1f, 0f, 0f, 1f)); // redish
    blastEffect.setEndColor(new ColorRGBA(1f, 1f, 0f, .1f)); // transparent white
    blastEffect.getParticleInfluencer().setInitialVelocity(new Vector3f(0, -2, 0));
    blastEffect.setStartSize(.4f);
    blastEffect.setEndSize(0.05f);
    blastEffect.setGravity(0, 0, 0);
    blastEffect.setLowLife(0.3f);
    blastEffect.setHighLife(.7f);
    blastEffect.getParticleInfluencer().setVelocityVariation(0.3f);

  }

}