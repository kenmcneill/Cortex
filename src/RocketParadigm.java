import com.jme3.audio.AudioData.DataType;

import java.util.Random;

import com.jme3.audio.AudioNode;
import com.jme3.audio.LowPassFilter;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh.Type;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class RocketParadigm extends FBParadigm {

  private static final float MIN_VOL = .2f;
  private static final float thresholdY = 4.1f;
  private float startingY = -3.7f;
  private Spatial rocket;
  private ParticleEmitter blastEffect;
  private Node rocketNode;

  private static final float THRUST_COEFF = .005f; // .005f;

  private int stage = 1;
  private long lastChange;
  Random random = new Random();

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

    rocketNode.attachChild(rocket);

    rootNode.attachChild(rocketNode);

    // DirectionalLight dLight = new DirectionalLight();
    // dLight.getDirection().set(-.2f, -.5f, .9f);
    // rootNode.addLight(dLight);

    audioNode = new AudioNode(assetManager, "assets/quietthrust.wav", DataType.Buffer);

    audioNode.setPositional(false);
    audioNode.setLooping(true);
    audioNode.setVolume(MIN_VOL);
    audioNode.setReverbEnabled(true);
    // audioNode.setDryFilter(new LowPassFilter(1f, .5f));
  }

  @Override
  void startParadigm() {

    rocketNode.attachChild(blastEffect);
    audioNode.play();

  }

  @Override
  void stopParadigm() {

    blastEffect.removeFromParent();
    audioNode.stop();
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

    float shimmy = (float) random.nextGaussian();
    
    rocketNode.move(shimmy * .002f, THRUST_COEFF * reward, 0f);
    rocketNode.rotate(0f, shimmy * .01f, 0f);
    
    updateFX();
  }

  void updateFX() {

    if (System.currentTimeMillis() - lastChange < 400) {
      return;
    }

    // vary the audio volume proportional to reward
    // will have no effect if volume is still ramping from previous call
    if (setVolume(reward > 0 ? reward * 1.1f : MIN_VOL)) {
      // sync to blast exhaust effect proportional to reward
      blastEffect.setStartSize(Math.max(reward * .4f, .2f));
      blastEffect.emitAllParticles();

    }

    lastChange = System.currentTimeMillis();
  }

  void initRocketGraphics() {

    rocket = assetManager.loadModel("assets/10475_Rocket_Ship_v1_L3.obj");
    rocket.setLocalScale(.01f);
    rocket.setLocalTranslation(0, 0, 0);
    rocket.rotate((float) -Math.PI / 2, 0, 0f);

    Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
    mat.setTexture("ColorMap", assetManager.loadTexture("assets/10475_Rocket_Ship_v1_Diffuse.jpg"));

    rocket.setMaterial(mat);

  }

  void initBlastEffect() {

    blastEffect = new ParticleEmitter("blast", Type.Triangle, 20);

    Material material = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");

    material.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/flame.png"));
    blastEffect.setMaterial(material);
    blastEffect.setImagesX(2);
    blastEffect.setImagesY(2); // 2x2 texture animation
    blastEffect.setStartColor(new ColorRGBA(1f, 0f, 0f, 1f)); // redish
    blastEffect.setEndColor(new ColorRGBA(1f, 1f, 1f, 0f)); // transparent white
    blastEffect.getParticleInfluencer().setInitialVelocity(new Vector3f(0, -2, 0));
    blastEffect.setStartSize(.1f);
    blastEffect.setEndSize(0.01f);
    blastEffect.setGravity(0, 0, 0);
    blastEffect.setHighLife(.8f);
    blastEffect.setLowLife(0.3f);
    blastEffect.getParticleInfluencer().setVelocityVariation(0.3f);

  }

}