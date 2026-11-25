package com.holub.life.plugin.view3d;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.font.BitmapText;
import com.jme3.input.ChaseCamera;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Torus;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.system.AppSettings;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * jMonkeyEngine window: lit columns with shadows, bloom on glowing cells, a camera that orbits with the mouse.
 * Drag to rotate, wheel to zoom, Space to pause the slow spin.
 */
final class JmeRenderer implements Renderer3d {
    private static final Logger ENGINE_LOG = Logger.getLogger("com.jme3");
    private static final int START_TIMEOUT_SECONDS = 20;
    /** Each column has a top and four sides; the bottom is never seen. */
    private static final int[][] FACES = {
            // {x sign, y sign, top?} for four corners, then the face normal as {x, up, y}
            {-1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1, 0, 1, 0},
            {1, -1, 0, 1, 1, 0, 1, 1, 1, 1, -1, 1, 1, 0, 0},
            {-1, -1, 0, -1, 1, 0, -1, 1, 1, -1, -1, 1, -1, 0, 0},
            {-1, 1, 0, 1, 1, 0, 1, 1, 1, -1, 1, 1, 0, 0, 1},
            {-1, -1, 0, 1, -1, 0, 1, -1, 1, -1, -1, 1, 0, 0, -1},
    };

    private final AtomicReference<Scene> pending = new AtomicReference<>();
    private final CountDownLatch ready = new CountDownLatch(1);
    private final App app = new App();
    private volatile Throwable startFailure;

    @Override public void start() {
        ENGINE_LOG.setLevel(Level.SEVERE);
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Life 3D  —  drag: rotate, wheel: zoom, Space: spin");
        settings.setResolution(1280, 720);
        settings.setResizable(true);
        settings.setVSync(true);
        settings.setSamples(4);
        settings.setAudioRenderer(null);
        // Colors are given as they should appear on screen, so no extra gamma step.
        settings.setGammaCorrection(false);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.setPauseOnLostFocus(false);
        app.start();
        try {
            if (!ready.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                startFailure = new IllegalStateException("3D window did not open in " + START_TIMEOUT_SECONDS + " s");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            startFailure = interrupted;
        }
        if (startFailure != null) {
            app.stop();
            throw new IllegalStateException("Could not start 3D graphics", startFailure);
        }
    }

    @Override public void show(Scene scene) { pending.set(scene); }

    @Override public void stop() { app.stop(); }

    /** Saves the next rendered frame as directory/name.png, for demonstrations and the report. */
    void screenshot(String directory, String name) {
        app.enqueue(() -> {
            ScreenshotAppState capture = new ScreenshotAppState(directory + java.io.File.separator, name);
            capture.setIsNumbered(false);
            app.getStateManager().attach(capture);
            capture.takeScreenshot();
        });
    }

    private final class App extends SimpleApplication {
        private final Node board = new Node("board");
        private final Node pillars = new Node("pillars");
        private final Map<String, Material> materials = new HashMap<>();
        private final Map<Material, ColorRGBA> pulsing = new HashMap<>();
        private ChaseCamera camera;
        private BitmapText caption;
        private Geometry base;
        private Scene shown;
        private boolean spinning = true;
        private float time;

        @Override public void simpleInitApp() {
            flyCam.setEnabled(false);
            setDisplayStatView(false);
            setDisplayFps(false);
            viewPort.setBackgroundColor(new ColorRGBA(0.02f, 0.03f, 0.06f, 1));

            DirectionalLight sun = new DirectionalLight();
            sun.setDirection(new Vector3f(-0.45f, -1f, -0.35f).normalizeLocal());
            sun.setColor(ColorRGBA.White.mult(1.1f));
            rootNode.addLight(sun);
            AmbientLight ambient = new AmbientLight();
            ambient.setColor(ColorRGBA.White.mult(0.45f));
            rootNode.addLight(ambient);

            FilterPostProcessor effects = new FilterPostProcessor(assetManager);
            effects.setNumSamples(context.getSettings().getSamples());
            DirectionalLightShadowFilter shadows = new DirectionalLightShadowFilter(assetManager, 2048, 3);
            shadows.setLight(sun);
            shadows.setShadowIntensity(0.45f);
            effects.addFilter(shadows);
            BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
            bloom.setBloomIntensity(2.2f);
            effects.addFilter(bloom);
            viewPort.addProcessor(effects);

            board.attachChild(pillars);
            rootNode.attachChild(board);
            camera = new ChaseCamera(cam, board, inputManager);
            camera.setSmoothMotion(false);
            camera.setInvertVerticalAxis(true);
            camera.setMinVerticalRotation(-FastMath.HALF_PI + 0.05f);
            camera.setDefaultVerticalRotation(0.75f);
            camera.setDefaultHorizontalRotation(FastMath.QUARTER_PI);

            caption = new BitmapText(guiFont);
            caption.setColor(new ColorRGBA(0.8f, 0.85f, 1f, 1f));
            guiNode.attachChild(caption);

            inputManager.addMapping("spin", new KeyTrigger(KeyInput.KEY_SPACE));
            inputManager.addListener((ActionListener) (name, pressed, tpf) -> {
                if (pressed) spinning = !spinning;
            }, "spin");
            ready.countDown();
        }

        @Override public void handleError(String message, Throwable failure) {
            startFailure = failure != null ? failure : new IllegalStateException(message);
            ready.countDown();
            stop();
        }

        @Override public void simpleUpdate(float tpf) {
            Scene next = pending.getAndSet(null);
            if (next != null) rebuild(next);

            time += tpf;
            if (spinning) board.rotate(0, 0.08f * tpf, 0);
            float beat = 0.25f + 0.75f * (0.5f + 0.5f * FastMath.sin(time * 7f));
            pulsing.forEach((material, color) -> material.setColor("GlowColor", color.mult(beat * 1.6f)));
            caption.setLocalTranslation(12, cam.getHeight() - 10, 0);
        }

        private void rebuild(Scene scene) {
            BoardLayout layout = new BoardLayout(scene.size(), scene.torus());
            if (shown == null || !shown.size().equals(scene.size()) || shown.torus() != scene.torus()) {
                rebuildBase(layout, scene);
                float extent = layout.extent();
                camera.setDefaultDistance(extent * (layout.torus() ? 3.4f : 2.3f));
                camera.setMinDistance(extent * 0.3f);
                camera.setMaxDistance(extent * 6f);
                cam.setFrustumFar(extent * 20f);
            }
            shown = scene;
            caption.setText(scene.caption());

            Map<String, List<Scene.Placed>> groups = new LinkedHashMap<>();
            for (Scene.Placed placed : scene.pillars())
                groups.computeIfAbsent(groupOf(placed.pillar()), key -> new ArrayList<>()).add(placed);
            pillars.detachAllChildren();
            groups.forEach((key, members) -> {
                Geometry geometry = new Geometry(key, columns(members, layout));
                geometry.setMaterial(materialOf(key, members.get(0).pillar()));
                geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
                pillars.attachChild(geometry);
            });
        }

        /** Columns that glow share a material per color; all others share one and differ by vertex color. */
        private String groupOf(Pillar pillar) {
            return pillar.glow() == Pillar.Glow.NONE ? "plain"
                    : pillar.glow() + ":" + pillar.red() + "," + pillar.green() + "," + pillar.blue();
        }

        private Material materialOf(String key, Pillar sample) {
            return materials.computeIfAbsent(key, created -> {
                Material material = lit(ColorRGBA.White);
                material.setBoolean("UseVertexColor", true);
                material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
                if (sample.glow() != Pillar.Glow.NONE) {
                    ColorRGBA glow = new ColorRGBA(sample.red(), sample.green(), sample.blue(), 1);
                    material.setColor("GlowColor", glow);
                    if (sample.glow() == Pillar.Glow.PULSE) pulsing.put(material, glow);
                }
                return material;
            });
        }

        private Material lit(ColorRGBA color) {
            Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            material.setBoolean("UseMaterialColors", true);
            material.setColor("Diffuse", color);
            material.setColor("Ambient", color);
            return material;
        }

        private void rebuildBase(BoardLayout layout, Scene scene) {
            if (base != null) base.removeFromParent();
            if (layout.torus()) {
                base = new Geometry("base", new Torus(96, 48, layout.tubeRadius() - 0.03f, layout.ringRadius()));
                base.rotate(FastMath.HALF_PI, 0, 0);
            } else {
                base = new Geometry("base", new Box(scene.size().width() / 2f, 0.1f, scene.size().height() / 2f));
                base.setLocalTranslation(0, -0.1f, 0);
            }
            base.setMaterial(lit(new ColorRGBA(0.13f, 0.16f, 0.24f, 1)));
            base.setShadowMode(RenderQueue.ShadowMode.Receive);
            board.attachChild(base);
        }

        /** One mesh for all columns of a group: five faces each, with face normals and the column color. */
        private Mesh columns(List<Scene.Placed> members, BoardLayout layout) {
            int faces = members.size() * FACES.length;
            FloatBuffer positions = BufferUtils.createFloatBuffer(faces * 4 * 3);
            FloatBuffer normals = BufferUtils.createFloatBuffer(faces * 4 * 3);
            FloatBuffer colors = BufferUtils.createFloatBuffer(faces * 4 * 4);
            IntBuffer indices = BufferUtils.createIntBuffer(faces * 6);
            int vertex = 0;
            for (Scene.Placed placed : members) {
                BoardLayout.Frame frame = layout.frame(placed.x(), placed.y());
                Pillar pillar = placed.pillar();
                float halfX = 0.45f * frame.sizeX(), halfY = 0.45f * frame.sizeY();
                for (int[] face : FACES) {
                    for (int corner = 0; corner < 4; corner++) {
                        float alongX = face[corner * 3] * halfX, alongY = face[corner * 3 + 1] * halfY;
                        float up = face[corner * 3 + 2] * pillar.height();
                        for (int axis = 0; axis < 3; axis++) {
                            positions.put(frame.origin()[axis] + alongX * frame.alongX()[axis]
                                    + up * frame.up()[axis] + alongY * frame.alongY()[axis]);
                            normals.put(face[12] * frame.alongX()[axis] + face[13] * frame.up()[axis]
                                    + face[14] * frame.alongY()[axis]);
                        }
                        colors.put(pillar.red()).put(pillar.green()).put(pillar.blue()).put(1f);
                    }
                    indices.put(vertex).put(vertex + 1).put(vertex + 2).put(vertex).put(vertex + 2).put(vertex + 3);
                    vertex += 4;
                }
            }
            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3, positions.flip());
            mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals.flip());
            mesh.setBuffer(VertexBuffer.Type.Color, 4, colors.flip());
            mesh.setBuffer(VertexBuffer.Type.Index, 3, indices.flip());
            mesh.updateBound();
            return mesh;
        }
    }
}
