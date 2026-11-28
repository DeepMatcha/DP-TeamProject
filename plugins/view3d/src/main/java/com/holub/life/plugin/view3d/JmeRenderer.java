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
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Torus;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.system.AppSettings;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * jMonkeyEngine window: columns that rise and sink smoothly between ticks, lit from two sides with
 * shadows and ambient occlusion, bloom on glowing cells, a cell grid on the floor, and a camera that
 * orbits with the mouse. Drag to rotate, wheel to zoom, Space to pause the slow spin.
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
    private static final int VERTICES_PER_COLUMN = FACES.length * 4;
    /** How quickly a column approaches its new height; about a tenth of a second to settle. */
    private static final float GROWTH_PER_SECOND = 14f;
    /** Columns are darker at the foot, which makes their height readable from above. */
    private static final float FOOT_SHADE = 0.45f;

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

    /** One group of columns that share a material, drawn as a single mesh whose buffers are reused. */
    private static final class ColumnGroup {
        final Geometry geometry;
        final Mesh mesh = new Mesh();
        FloatBuffer positions, normals, colors;
        IntBuffer indices;
        int capacity;
        int columns;

        ColumnGroup(String name, Material material) {
            geometry = new Geometry(name, mesh);
            geometry.setMaterial(material);
            geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            begin(1);
        }

        void begin(int needed) {
            if (needed > capacity) {
                capacity = Math.max(needed, capacity * 2);
                positions = BufferUtils.createFloatBuffer(capacity * VERTICES_PER_COLUMN * 3);
                normals = BufferUtils.createFloatBuffer(capacity * VERTICES_PER_COLUMN * 3);
                colors = BufferUtils.createFloatBuffer(capacity * VERTICES_PER_COLUMN * 4);
                indices = BufferUtils.createIntBuffer(capacity * FACES.length * 6);
            }
            positions.clear();
            normals.clear();
            colors.clear();
            indices.clear();
            columns = 0;
        }

        void add(BoardLayout.Frame frame, float height, float red, float green, float blue) {
            float halfX = 0.45f * frame.sizeX(), halfY = 0.45f * frame.sizeY();
            int vertex = columns * VERTICES_PER_COLUMN;
            for (int[] face : FACES) {
                for (int corner = 0; corner < 4; corner++) {
                    float alongX = face[corner * 3] * halfX, alongY = face[corner * 3 + 1] * halfY;
                    boolean top = face[corner * 3 + 2] == 1;
                    float up = top ? height : 0;
                    for (int axis = 0; axis < 3; axis++) {
                        positions.put(frame.origin()[axis] + alongX * frame.alongX()[axis]
                                + up * frame.up()[axis] + alongY * frame.alongY()[axis]);
                        normals.put(face[12] * frame.alongX()[axis] + face[13] * frame.up()[axis]
                                + face[14] * frame.alongY()[axis]);
                    }
                    float shade = top ? 1f : FOOT_SHADE;
                    colors.put(red * shade).put(green * shade).put(blue * shade).put(1f);
                }
                indices.put(vertex).put(vertex + 1).put(vertex + 2).put(vertex).put(vertex + 2).put(vertex + 3);
                vertex += 4;
            }
            columns++;
        }

        void end() {
            geometry.setCullHint(columns == 0 ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
            mesh.setBuffer(VertexBuffer.Type.Position, 3, positions.flip());
            mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals.flip());
            mesh.setBuffer(VertexBuffer.Type.Color, 4, colors.flip());
            mesh.setBuffer(VertexBuffer.Type.Index, 3, indices.flip());
            mesh.updateCounts();
            mesh.updateBound();
            geometry.updateModelBound();
        }
    }

    private final class App extends SimpleApplication {
        private final Node board = new Node("board");
        private final Node pillars = new Node("pillars");
        private final Map<String, ColumnGroup> groups = new HashMap<>();
        private final Map<Material, ColorRGBA> pulsing = new HashMap<>();
        private ChaseCamera camera;
        private BitmapText caption;
        private Geometry base;
        private Geometry grid;
        private Scene shown;
        private BoardLayout layout;
        /** Per cell: the column it is becoming (null for bare floor), the one it last was, and its height now. */
        private Pillar[] target = new Pillar[0];
        private Pillar[] last = new Pillar[0];
        private float[] height = new float[0];
        private boolean moving;
        private boolean spinning = true;
        private float time;

        @Override public void simpleInitApp() {
            flyCam.setEnabled(false);
            setDisplayStatView(false);
            setDisplayFps(false);
            viewPort.setBackgroundColor(new ColorRGBA(0.015f, 0.02f, 0.045f, 1));

            // A warm key light from above and a cool, weaker light from the other side.
            DirectionalLight sun = new DirectionalLight();
            sun.setDirection(new Vector3f(-0.45f, -1f, -0.35f).normalizeLocal());
            sun.setColor(new ColorRGBA(1f, 0.94f, 0.84f, 1f).mult(1.05f));
            rootNode.addLight(sun);
            DirectionalLight fill = new DirectionalLight();
            fill.setDirection(new Vector3f(0.6f, -0.35f, 0.7f).normalizeLocal());
            fill.setColor(new ColorRGBA(0.35f, 0.5f, 0.9f, 1f).mult(0.45f));
            rootNode.addLight(fill);
            AmbientLight ambient = new AmbientLight();
            ambient.setColor(ColorRGBA.White.mult(0.38f));
            rootNode.addLight(ambient);

            FilterPostProcessor effects = new FilterPostProcessor(assetManager);
            effects.setNumSamples(context.getSettings().getSamples());
            DirectionalLightShadowFilter shadows = new DirectionalLightShadowFilter(assetManager, 2048, 3);
            shadows.setLight(sun);
            shadows.setShadowIntensity(0.5f);
            effects.addFilter(shadows);
            effects.addFilter(new SSAOFilter(1.6f, 2.2f, 0.35f, 0.12f));
            BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
            bloom.setBloomIntensity(2.4f);
            bloom.setBlurScale(1.8f);
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
            if (next != null) retarget(next);
            if (moving) grow(tpf);

            time += tpf;
            if (spinning) board.rotate(0, 0.08f * tpf, 0);
            float beat = 0.25f + 0.75f * (0.5f + 0.5f * FastMath.sin(time * 7f));
            pulsing.forEach((material, color) -> material.setColor("GlowColor", color.mult(beat * 1.6f)));
            caption.setLocalTranslation(12, cam.getHeight() - 10, 0);
        }

        /** A new board only sets where each column should end up; grow() moves it there over a few frames. */
        private void retarget(Scene scene) {
            int cells = scene.size().area();
            if (shown == null || !shown.size().equals(scene.size()) || shown.torus() != scene.torus()) {
                layout = new BoardLayout(scene.size(), scene.torus());
                rebuildBase(scene);
                last = new Pillar[cells];
                height = new float[cells];
                float extent = layout.extent();
                camera.setDefaultDistance(extent * (layout.torus() ? 3.4f : 2.3f));
                camera.setMinDistance(extent * 0.3f);
                camera.setMaxDistance(extent * 6f);
                cam.setFrustumFar(extent * 20f);
            }
            shown = scene;
            caption.setText(scene.caption());
            target = new Pillar[cells];
            for (Scene.Placed placed : scene.pillars()) {
                int cell = placed.y() * scene.size().width() + placed.x();
                target[cell] = placed.pillar();
                last[cell] = placed.pillar();
            }
            moving = true;
        }

        private void grow(float tpf) {
            float step = Math.min(1f, tpf * GROWTH_PER_SECOND);
            int width = shown.size().width();
            moving = false;
            Map<String, Integer> counts = new HashMap<>();
            for (int cell = 0; cell < height.length; cell++) {
                float goal = target[cell] == null ? 0 : target[cell].height();
                height[cell] += (goal - height[cell]) * step;
                if (Math.abs(goal - height[cell]) < 0.01f) height[cell] = goal;
                else moving = true;
                if (height[cell] > 0) counts.merge(groupOf(last[cell]), 1, Integer::sum);
            }
            groups.values().forEach(group -> group.begin(0));
            for (int cell = 0; cell < height.length; cell++) {
                if (height[cell] <= 0) continue;
                Pillar pillar = last[cell];
                String key = groupOf(pillar);
                ColumnGroup group = groups.get(key);
                if (group == null) {
                    group = new ColumnGroup(key, columnMaterial(pillar));
                    groups.put(key, group);
                    pillars.attachChild(group.geometry);
                }
                if (group.columns == 0) group.begin(counts.get(key));
                group.add(layout.frame(cell % width, cell / width), height[cell],
                        pillar.red(), pillar.green(), pillar.blue());
            }
            groups.values().forEach(ColumnGroup::end);
        }

        /** Columns that glow share a material per color; all others share one and differ by vertex color. */
        private String groupOf(Pillar pillar) {
            return pillar.glow() == Pillar.Glow.NONE ? "plain"
                    : pillar.glow() + ":" + pillar.red() + "," + pillar.green() + "," + pillar.blue();
        }

        private Material columnMaterial(Pillar sample) {
            Material material = lit(ColorRGBA.White);
            material.setBoolean("UseVertexColor", true);
            material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            if (sample.glow() != Pillar.Glow.NONE) {
                ColorRGBA glow = new ColorRGBA(sample.red(), sample.green(), sample.blue(), 1);
                material.setColor("GlowColor", glow);
                if (sample.glow() == Pillar.Glow.PULSE) pulsing.put(material, glow);
            }
            return material;
        }

        private Material lit(ColorRGBA color) {
            Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            material.setBoolean("UseMaterialColors", true);
            material.setColor("Diffuse", color);
            material.setColor("Ambient", color);
            return material;
        }

        private void rebuildBase(Scene scene) {
            if (base != null) base.removeFromParent();
            if (grid != null) grid.removeFromParent();
            if (layout.torus()) {
                base = new Geometry("base", new Torus(96, 48, layout.tubeRadius() - 0.03f, layout.ringRadius()));
                base.rotate(FastMath.HALF_PI, 0, 0);
            } else {
                base = new Geometry("base", new Box(scene.size().width() / 2f, 0.1f, scene.size().height() / 2f));
                base.setLocalTranslation(0, -0.1f, 0);
            }
            base.setMaterial(lit(new ColorRGBA(0.10f, 0.13f, 0.21f, 1)));
            base.setShadowMode(RenderQueue.ShadowMode.Receive);
            board.attachChild(base);

            grid = new Geometry("grid", cellOutlines(scene));
            Material lines = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            lines.setColor("Color", new ColorRGBA(0.2f, 0.27f, 0.42f, 1));
            grid.setMaterial(lines);
            grid.setShadowMode(RenderQueue.ShadowMode.Off);
            board.attachChild(grid);
        }

        /** The outline of every cell, lifted slightly off the surface so it is not hidden by it. */
        private Mesh cellOutlines(Scene scene) {
            int cells = scene.size().area();
            FloatBuffer positions = BufferUtils.createFloatBuffer(cells * 4 * 3);
            IntBuffer indices = BufferUtils.createIntBuffer(cells * 8);
            int[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            int vertex = 0;
            for (int y = 0; y < scene.size().height(); y++)
                for (int x = 0; x < scene.size().width(); x++) {
                    BoardLayout.Frame frame = layout.frame(x, y);
                    for (int[] corner : corners)
                        for (int axis = 0; axis < 3; axis++)
                            positions.put(frame.origin()[axis] + 0.04f * frame.up()[axis]
                                    + corner[0] * 0.5f * frame.sizeX() * frame.alongX()[axis]
                                    + corner[1] * 0.5f * frame.sizeY() * frame.alongY()[axis]);
                    for (int edge = 0; edge < 4; edge++) indices.put(vertex + edge).put(vertex + (edge + 1) % 4);
                    vertex += 4;
                }
            Mesh mesh = new Mesh();
            mesh.setMode(Mesh.Mode.Lines);
            mesh.setBuffer(VertexBuffer.Type.Position, 3, positions.flip());
            mesh.setBuffer(VertexBuffer.Type.Index, 2, indices.flip());
            mesh.updateBound();
            return mesh;
        }
    }
}
