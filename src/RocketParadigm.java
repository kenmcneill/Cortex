import com.jme3.audio.AudioData.DataType;
import com.jme3.audio.AudioNode;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh.Type;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class RocketParadigm extends FBParadigm {

  private Spatial rocket;
  private ParticleEmitter blastEffect;
  private Node rocketNode;

  private static final float THRUST_COEFF = .005f;

  private AudioNode thrustAudio;
  private long lastVolumeChange;
  
    RocketParadigm() {
        super();
    }


  void initParadigm() {

    rocketNode = new Node();
    // top.scale(.6f);
    rocketNode.setLocalScale(.5f);
    rocketNode.setLocalTranslation(0, -3.5f, 0);

    initRocketGraphics();
    initBlastEffect();

    rocketNode.attachChild(rocket);
    rocketNode.attachChild(blastEffect);

    rootNode.attachChild(rocketNode);

    thrustAudio = new AudioNode(assetManager, "assets/quietthrust.wav", DataType.Buffer);
    thrustAudio.setLooping(true);
    thrustAudio.setPositional(false);
    thrustAudio.setVolume(.1f);
    thrustAudio.play();

    blastEffect.emitAllParticles();

  }

  void updateParadigm(long now) {

    rocketNode.move(0, THRUST_COEFF * rewardEMWA, 0);

    if (now - lastVolumeChange < 250) {
      return;
    }

    float low = .3f;
    float med = .6f;
    float hi = 1f;

    if (rewardEMWA <= low) {
      thrustAudio.setVolume(low);
      blastEffect.setParticlesPerSec(15);
    } else if (rewardEMWA <= med) {
      thrustAudio.setVolume(med);
      blastEffect.setParticlesPerSec(30);
    } else {
      thrustAudio.setVolume(hi);
      blastEffect.setParticlesPerSec(45);
    }
    lastVolumeChange = now;
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
    blastEffect = new ParticleEmitter("blast", Type.Triangle, 15);

    Material material = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");

    material.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/flame.png"));
    blastEffect.setMaterial(material);
    blastEffect.setImagesX(2);
    blastEffect.setImagesY(2); // 2x2 texture animation
    blastEffect.setStartColor(new ColorRGBA(1f, 1f, 1f, 1f)); // white
    blastEffect.setEndColor(new ColorRGBA(0f, 0f, 1f, .2f)); // blueish
    blastEffect.getParticleInfluencer().setInitialVelocity(new Vector3f(0, -3, 0));
    blastEffect.setStartSize(.4f);
    blastEffect.setEndSize(0.1f);
    blastEffect.setGravity(0, 0, 0);
    blastEffect.setLowLife(0.5f);
    blastEffect.setHighLife(1f);
    blastEffect.getParticleInfluencer().setVelocityVariation(0.2f);

    blastEffect.setParticlesPerSec(15);

    rocketNode.attachChild(blastEffect);
  }


}