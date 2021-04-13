import com.jme3.audio.AudioData.DataType;
import com.jme3.audio.AudioNode;
import com.jme3.audio.LowPassFilter;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.DepthOfFieldFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.FogFilter;
import com.jme3.post.filters.LightScatteringFilter;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture.WrapMode;
import com.jme3.texture.Texture2D;
import com.jme3.util.SkyFactory;
import com.jme3.util.SkyFactory.EnvMapType;
import com.jme3.water.WaterFilter;

/**
 * test
 *
 * @author normenhansen
 */
public class OceanParadigm extends FBParadigm {

    private static final float AMP_INC = .02f;
    private static final float FOG_INC = .005f;
    final private Vector3f lightDir = new Vector3f(-4.9236743f, -1.27054665f, 5.896916f);
    private WaterFilter water;
    private TerrainQuad terrain;
    private Material matRock;
    final private LowPassFilter aboveWaterAudioFilter = new LowPassFilter(1, 1);

    // This part is to emulate tides, slightly varrying the height of the water
    private float time = 0.0f;
    private float waterHeight = 0.0f;
    final private float initialWaterHeight = 90f;// 0.8f;
    private float amplitudeTarget;
    private FogFilter fog;
    private float fogTarget;

    public static void main(String[] args) {

        OceanParadigm app = new OceanParadigm();
        app.start();
    }

    @Override
    public void initParadigm() {

        setDisplayFps(false);
        setDisplayStatView(false);

        Node mainScene = new Node("Main Scene");
        rootNode.attachChild(mainScene);

        createTerrain(mainScene);

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(lightDir);
        sun.setColor(ColorRGBA.White.clone().multLocal(1f));
        mainScene.addLight(sun);

        AmbientLight al = new AmbientLight();
        al.setColor(ColorRGBA.White.mult(.5f));
        mainScene.addLight(al);

        cam.setLocation(new Vector3f(-370.31592f, 120, 196.81192f));
        cam.setRotation(new Quaternion(0.015302252f, 0.9304095f, -0.039101653f, 0.3641086f));

        Spatial sky = SkyFactory.createSky(assetManager, "Scenes/Beach/FullskiesSunset0068.dds", EnvMapType.CubeMap);
        // sky.setLocalScale(350);

        mainScene.attachChild(sky);
        cam.setFrustumFar(4000);

        // Water Filter
        water = new WaterFilter(mainScene, lightDir);

        water.setWaterTransparency(0.005f);
        water.setFoamIntensity(0.8f);
        water.setFoamHardness(.5f);
        water.setFoamExistence(new Vector3f(2f, 8f, 1f));
        water.setSpeed(0); // .6f
        water.setRefractionConstant(0.1f);
        water.setWaveScale(0.01f); // .01f
        water.setMaxAmplitude(0f); // 3f
        water.setFoamTexture((Texture2D) assetManager.loadTexture("Common/MatDefs/Water/Textures/foam2.jpg"));
        water.setRefractionStrength(0); // 0
        water.setWaterHeight(90);

        water.getWindDirection().set(.1f, -1f);

        // Bloom Filter
        BloomFilter bloom = new BloomFilter();
        bloom.setExposurePower(55);
        bloom.setBloomIntensity(.5f);

        // Light Scattering Filter
        LightScatteringFilter lsf = new LightScatteringFilter(lightDir.mult(-300));
        lsf.setLightDensity(0.5f);

        // Depth of field Filter
        DepthOfFieldFilter dof = new DepthOfFieldFilter();
        dof.setFocusDistance(50);
        dof.setFocusRange(100);

        fog = new FogFilter();
        fog.setFogColor(new ColorRGBA(0.3f, 0.3f, 0.3f, 0f));
        fog.setFogDistance(155);
        fog.setFogDensity(1f);

        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);

        fpp.addFilter(water);
        // fpp.addFilter(bloom);
        // fpp.addFilter(lsf);
        // fpp.addFilter(dof);
        fpp.addFilter(fog);

        fpp.addFilter(new FXAAFilter());

        int numSamples = getContext().getSettings().getSamples();
        if (numSamples > 0) {
            fpp.setNumSamples(numSamples);
        }

        audioNode = new AudioNode(assetManager, "Sound/Environment/Ocean Waves.ogg", DataType.Buffer);
        audioNode.setLooping(true);
        audioNode.setReverbEnabled(true);
        audioNode.setDryFilter(aboveWaterAudioFilter);

        //
        viewPort.addProcessor(fpp);

    }

    private void createTerrain(Node rootNode) {

        matRock = new Material(assetManager, "Common/MatDefs/Terrain/TerrainLighting.j3md");
        matRock.setBoolean("useTriPlanarMapping", false);
        matRock.setBoolean("WardIso", true);
        matRock.setTexture("AlphaMap", assetManager.loadTexture("Textures/Terrain/splat/alphamap.png"));
        Texture heightMapImage = assetManager.loadTexture("Textures/Terrain/splat/mountains512.png");
        Texture grass = assetManager.loadTexture("Textures/Terrain/splat/grass.jpg");
        grass.setWrap(WrapMode.Repeat);
        matRock.setTexture("DiffuseMap", grass);
        matRock.setFloat("DiffuseMap_0_scale", 64);
        Texture dirt = assetManager.loadTexture("Textures/Terrain/splat/dirt.jpg");
        dirt.setWrap(WrapMode.Repeat);
        matRock.setTexture("DiffuseMap_1", dirt);
        matRock.setFloat("DiffuseMap_1_scale", 16);
        Texture rock = assetManager.loadTexture("Textures/Terrain/splat/road.jpg");
        rock.setWrap(WrapMode.Repeat);
        matRock.setTexture("DiffuseMap_2", rock);
        matRock.setFloat("DiffuseMap_2_scale", 128);
        Texture normalMap0 = assetManager.loadTexture("Textures/Terrain/splat/grass_normal.jpg");
        normalMap0.setWrap(WrapMode.Repeat);
        Texture normalMap1 = assetManager.loadTexture("Textures/Terrain/splat/dirt_normal.png");
        normalMap1.setWrap(WrapMode.Repeat);
        Texture normalMap2 = assetManager.loadTexture("Textures/Terrain/splat/road_normal.png");
        normalMap2.setWrap(WrapMode.Repeat);
        matRock.setTexture("NormalMap", normalMap0);
        matRock.setTexture("NormalMap_1", normalMap1);
        matRock.setTexture("NormalMap_2", normalMap2);

        AbstractHeightMap heightmap = null;
        try {
            heightmap = new ImageBasedHeightMap(heightMapImage.getImage(), 0.25f);
            heightmap.load();
        } catch (Exception e) {
            e.printStackTrace();
        }
        terrain = new TerrainQuad("terrain", 65, 513, heightmap.getHeightMap());
        terrain.setMaterial(matRock);
        terrain.setLocalScale(new Vector3f(5, 5, 5));
        terrain.setLocalTranslation(new Vector3f(0, -30, 0));
        // terrain.setLocked(false); // unlock it so we can edit the height

        terrain.setShadowMode(ShadowMode.Receive);
        rootNode.attachChild(terrain);

    }

    @Override
    public void updateParadigm(boolean sync, long duration) {

        time += .02f;
        waterHeight = (float) Math.cos(((time * 0.6f) % FastMath.TWO_PI)) * 1.5f; // .6f, 1.5f
        water.setWaterHeight(initialWaterHeight + waterHeight);

        updateAmplitude();

        // invert reward and dont reward negative
        float invertValue = reward < 0 ? .9f : (1 - reward);

        // setVolume(invertValue * .5f);
        amplitudeTarget = invertValue *2f;
        fogTarget = invertValue;

    }

    private void updateAmplitude() {
        
        float value = water.getMaxAmplitude();
        float updatedValue = FBParadigm.updateIncrementalValue(value, amplitudeTarget, AMP_INC);

        water.setMaxAmplitude(updatedValue);
        // water.setWaterHeight(initialWaterHeight);

        value = fog.getFogDensity();
        updatedValue = FBParadigm.updateIncrementalValue(value, fogTarget, FOG_INC);
        fog.setFogDensity(updatedValue);

    }

    @Override
    void startParadigm() {

        audioNode.play();
        water.setSpeed(2f);
        water.setMaxAmplitude(1f); // 3f
    }

    @Override
    void stopParadigm() {

        audioNode.stop();
        water.setSpeed(0f);
        water.setMaxAmplitude(0f); // 3f
    }
}
