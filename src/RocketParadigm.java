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
  private ParticleEmitter fire;
  private Node top;

  private static final float THRUST_COEFF = .04f;

  private AudioNode thrustAudio;
  
    RocketParadigm() {
        super();
    }


  void initParadigm() {

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
    thrustAudio.play();

    fire.emitAllParticles();

  }

  void updateParadigm() {

    top.move(0, THRUST_COEFF * rewardEMWA, 0);
    
    if (rewardEMWA > .6) {
      thrustAudio.setVolume(1f);
      fire.setParticlesPerSec(40);
    } else {
      thrustAudio.setVolume(.5f);
      fire.setParticlesPerSec(20);
    }

    updateRewardBar();

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

    Material material = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");

    material.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/flame.png"));
    fire.setMaterial(material);
    fire.setImagesX(2);
    fire.setImagesY(2); // 2x2 texture animation
    fire.setStartColor(new ColorRGBA(1f, .4f, .4f, 1f)); // redish
    fire.setEndColor(new ColorRGBA(1f, 1f, 1f, 0.2f)); // white
    fire.getParticleInfluencer().setInitialVelocity(new Vector3f(0, -3, 0));
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
  }


}