import com.jme3.audio.AudioData.DataType;
import com.jme3.audio.AudioNode;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
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
import com.jme3.scene.shape.Torus;
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

    // final private Vector3f lightDir = new Vector3f(-4.9236743f, -1.27054665f,
    // 5.896916f);

    private static final float INITIAL_FOG_DENSITY = 1f;
    private Vector3f lightDir;
    private WaterFilter water;
    private TerrainQuad terrain;
    private Material matRock;

    // This part is to emulate tides, slightly varrying the height of the water
    private float time = 0.0f;
    final private float seaLevel = 36.5f;// 0.8f;
    final private float sunStartY = seaLevel - 7f;
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

        if (flyCam != null) {
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
        ambLight.setColor(ColorRGBA.Yellow.mult(.5f));
        mainScene.addLight(ambLight);

        cam.setLocation(new Vector3f(0, seaLevel + 2.5f, 5));

        sky = SkyFactory.createSky(assetManager, "Scenes/Beach/FullskiesSunset0068.dds", EnvMapType.CubeMap);
        sky.rotate(0, (float) Math.PI, 0);

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
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
        bloom.setExposurePower(5); // 55, 40 , 5
        bloom.setBloomIntensity(1.2f); // 1.2f
        bloom.setDownSamplingFactor(3f);

        lsf = new LightScatteringFilter(cam.getDirection().negate().multLocal(1000));
        lsf.setLightDensity(1.2f); // 1.2

        fog = new FogFilter();
        fog.setFogColor(new ColorRGBA(0.5f, 0.5f, 0.5f, 0f));
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

        mat.setColor("Color", ColorRGBA.Yellow.mult(2f));
        mat.setColor("GlowColor", ColorRGBA.Yellow.mult(2f));

        sunGeom = new Geometry();

        sunGeom.setMaterial(mat);

        Sphere sphere = new Sphere(256, 256, 8f);
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

        // a nice view from here
        terrain.setLocalRotation(new Quaternion(0, .494f, 0, -.869f));

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

        float sunFactor = (sunGeom.getWorldTranslation().y - sunStartY) / sunDisplacement;

        if (sunFactor > 1) {
            finish();
            return;
        }

        float c1 = reward * coeff; // .01 for non test;
        float c2 = c1 * .1f;

        sunGeom.move(0, c1, 0);
        lsf.setLightPosition(sunGeom.getWorldTranslation().mult(100));

        Vector3f dir = water.getLightDirection();
        dir.addLocal(0, 0, c2);

        water.setLightDirection(dir);
        sunLight.setDirection(dir);

        fog.setFogDensity(INITIAL_FOG_DENSITY - sunFactor);

        ambLight.setColor(ColorRGBA.Yellow.mult(.4f + sunFactor));

        if (rotate)
            terrain.rotate(0, .01f, 0);

    }

    void finish() {

        stopParadigm();

        Torus torus = new Torus(128, 128, .3f, 40f);
        Geometry rainbow = new Geometry("rainbow", torus);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");

        float t = 1f;

        mat.setColor("Color", new ColorRGBA(1, 0, 0, t));
        mat.setColor("GlowColor", ColorRGBA.Red);

        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        mat.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Front);

        rainbow.setMaterial(mat);
        rainbow.setQueueBucket(Bucket.Sky);

        rainbow.move(0, seaLevel - 10, -100);

        rootNode.attachChild(rainbow);

        float s = .985f;
        Geometry rb = null;
        ColorRGBA color = null;

        rb = rainbow.clone();

        color = new ColorRGBA(1f, .27f, 0, t);
        rb.getMaterial().setColor("Color", color);
        rb.getMaterial().setColor("GlowColor", color);
        rb.scale(s);
        rootNode.attachChild(rb);

        rb = rb.clone();
        color = new ColorRGBA(1f, 1f, 0, t);
        rb.getMaterial().setColor("Color", color);
        rb.getMaterial().setColor("GlowColor", color);
        rb.scale(s);
        rootNode.attachChild(rb);

        rb = rb.clone();
        color = new ColorRGBA(0f, 0f, 1f, t);
        rb.getMaterial().setColor("Color", color);
        rb.getMaterial().setColor("GlowColor", color);
        rb.scale(s);
        rootNode.attachChild(rb);

        rb = rb.clone();
        color = new ColorRGBA(.3f, 0f, .5f, t);

        rb.getMaterial().setColor("Color", color);
        rb.getMaterial().setColor("GlowColor", color);
        rb.scale(s);
        rootNode.attachChild(rb);

        rb = rb.clone();
        color = new ColorRGBA(.5f, 0f, 1f, t);

        rb.getMaterial().setColor("Color", color);
        rb.getMaterial().setColor("GlowColor", color);
        rb.scale(s);
        rootNode.attachChild(rb);

        super.finish();
    }

    @Override
    void startParadigm() {

        if (testMode) {
            coeff = 1f;
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

    }

    @Override
    void resetParadigm() {
        // TODO: implement
    }
}
