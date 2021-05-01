import com.jme3.audio.AudioData.DataType;
import com.jme3.audio.AudioNode;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.FogFilter;
import com.jme3.post.filters.LightScatteringFilter;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Sphere;
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

    //final private Vector3f lightDir = new Vector3f(-4.9236743f, -1.27054665f, 5.896916f);

    private static final float INITIAL_FOG_DENSITY = 1.4f;
    private Vector3f lightDir;
    private WaterFilter water;
    private TerrainQuad terrain;
    private Material matRock;

    // This part is to emulate tides, slightly varrying the height of the water
    private float time = 0.0f;
    final private float seaLevel = 36.5f;// 0.8f;
    final private float sunStartY = seaLevel -7f;
    final private float sunEndY = 96f;
    final private float sunDisplacement = sunEndY - sunStartY;

    private float waterDisplacement = 0;

    private FogFilter fog;
    private Spatial sky;
    private Node mainScene;
    private Geometry sunGeom;
    private DirectionalLight sunLight;
    private AmbientLight ambLight;
    private LightScatteringFilter lsf;
    private float coeff = 0;
    private boolean rotate;

    public static void main(String[] args) {

        OceanParadigm app = new OceanParadigm();
        app.start();
    }

    @Override
    public void initParadigm() {

        invertVol = true;
        
        if (flyCam !=null) {
            flyCam.setMoveSpeed(20);
            flyCam.setRotationSpeed(.5f);
    
        }

        mainScene = new Node("Main Scene");
        rootNode.attachChild(mainScene);

        createTerrain();
        createSun();

        sunLight = new DirectionalLight();

        lightDir = new Vector3f(0, -1, .5f);
        sunLight.setDirection(lightDir);
        sunLight.setColor(ColorRGBA.White.mult(.1f));
        mainScene.addLight(sunLight);

        ambLight = new AmbientLight();
        ambLight.setColor(ColorRGBA.Yellow.mult(.01f));
        mainScene.addLight(ambLight);

        cam.setLocation(new Vector3f(0, seaLevel+2.5f, 5));
        // cam.setLocation(new Vector3f(-370.31592f, 120, 196.81192f));
        // cam.setRotation(new Quaternion(0.015302252f, 0.9304095f, -0.039101653f, 0.3641086f));

        sky = SkyFactory.createSky(assetManager, "Scenes/Beach/FullskiesSunset0068.dds", EnvMapType.CubeMap);
        // sky = SkyFactory.createSky(assetManager, "assets/sky1.jpg", EnvMapType.SphereMap);
        sky.rotate(0, (float)Math.PI, 0);

        mainScene.attachChild(sky);
        cam.setFrustumFar(4000);

        audioNode = new AudioNode(assetManager, "Sound/Environment/Ocean Waves.ogg", DataType.Buffer);
        audioNode.setLooping(true);
        audioNode.setReverbEnabled(true);

        addFx();

    }

    void addFx() {
        // Water Filter
        water = new WaterFilter(mainScene, lightDir);

        water.setWaterTransparency(0.02f);
        water.setFoamIntensity(0.8f);
        water.setFoamHardness(2); // .5f
        water.setFoamExistence(new Vector3f(2f, 6f, 1f));
        water.setSpeed(0); // .6f
        water.setRefractionConstant(0.1f);
        water.setWaveScale(0.01f); // .01f
        water.setMaxAmplitude(0f); // 3f
        water.setFoamTexture((Texture2D) assetManager.loadTexture("Common/MatDefs/Water/Textures/foam2.jpg"));
        water.setRefractionStrength(0); // 0
        water.setWaterHeight(seaLevel);

        water.getWindDirection().set(.1f, -1f);

        water.setLightColor(ColorRGBA.Yellow.mult(.1f));

        // Bloom Filter
        BloomFilter bloom = new BloomFilter();
        bloom.setExposurePower(40); // 55
        bloom.setBloomIntensity(3); // 1.2f

        lsf = new LightScatteringFilter(cam.getDirection().negate().multLocal(1000));
        lsf.setLightDensity(1.2f);

        fog = new FogFilter();
        fog.setFogColor(new ColorRGBA(0.3f, 0.3f, 0.3f, 0f));
        fog.setFogDistance(200);
        fog.setFogDensity(INITIAL_FOG_DENSITY);

        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);

        fpp.addFilter(water);
        fpp.addFilter(bloom);
        fpp.addFilter(lsf);
        fpp.addFilter(fog);

        fpp.addFilter(new FXAAFilter());

        int numSamples = getContext().getSettings().getSamples();
        if (numSamples > 0) {
            fpp.setNumSamples(numSamples);
        }
        viewPort.addProcessor(fpp);

    }

    private void createSun() {
        
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");

        mat.setColor("Color", ColorRGBA.Yellow);
        mat.setColor("GlowColor", ColorRGBA.Yellow);

        sunGeom = new Geometry();
        
        sunGeom.setMaterial(mat);

        Sphere sphere = new Sphere(16, 64, 10f);
        sunGeom.setMesh(sphere);

        sunGeom.setQueueBucket(Bucket.Sky);
        sunGeom.setCullHint(Spatial.CullHint.Never);
        sunGeom.setLocalTranslation(new Vector3f(0, sunStartY, -100));

        rootNode.attachChild(sunGeom);
    }

    private void createTerrain() {

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
        terrain.setLocalScale(new Vector3f(4, 1, 4));

        terrain.setShadowMode(ShadowMode.Receive);
        mainScene.attachChild(terrain);

    }

    @Override
    public void updateParadigm(boolean sync) {

        time += .01f;
        waterDisplacement = (float) Math.sin(time % FastMath.TWO_PI) * 1f; // .6f, 1.5f
        water.setWaterHeight(seaLevel + waterDisplacement);

        // invert reward and don't reward negative
        float invertValue = smoothChangeValue < 0 ? 1f : (1 - smoothChangeValue);

        water.setMaxAmplitude(invertValue);

        float c1 = reward * coeff; // .01 for non test;
        float c2 = c1 *.1f;

        sunGeom.move(0, c1, 0); 
        lsf.setLightPosition(sunGeom.getWorldTranslation().mult(100));

        float sunFactor = (sunGeom.getWorldTranslation().y - sunStartY)/sunDisplacement;
        
        Vector3f dir = water.getLightDirection();
        dir.addLocal(0, 0, c2);

        water.setLightDirection(dir);
        sunLight.setDirection(dir);

        fog.setFogDensity(INITIAL_FOG_DENSITY - sunFactor);
        ambLight.setColor(ColorRGBA.White.mult(sunFactor));

        if (rotate) terrain.rotate(0, .01f, 0);


    }

    @Override
    void startParadigm() {

        if (testMode) {
            coeff = .1f;
        } else {
            coeff = .01f;
        }

        audioNode.play();
        water.setSpeed(2f);
        water.setMaxAmplitude(1f); // 3f
    }

    @Override
    void stopParadigm() {

        audioNode.stop();
        water.setSpeed(0f);
        water.setMaxAmplitude(0f); // 3f

        System.out.println(terrain.getLocalRotation());

    }

    @Override
    void resetParadigm() {
        rotate = !rotate;
    }
}
