import com.holub.life.Life;
import com.holub.life.host.PluginDirectory;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Smoke checks against the shipped host JAR and its optional plug-in directory. */
public final class DesktopMergeAcceptance {
    private static final Set<String> REQUIRED_PROVIDERS = Set.of(
            "codec", "foodaging", "obstacle", "toroidal", "view2d", "view3d");
    private static final String GENERATION_PREFIX = "Generation: ";
    private static final int BOARD_SIZE = 64;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Specify desktop, providers or plugins");
        }
        switch (arguments[0]) {
            case "desktop" -> desktop();
            case "providers" -> providers();
            case "plugins" -> plugins();
            default -> throw new IllegalArgumentException("Unknown acceptance mode: " + arguments[0]);
        }
    }

    private static void providers() throws Exception {
        try (var directory = PluginDirectory.open()) {
            Map<String, String> providers = new TreeMap<>();
            for (var plugin : directory.plugins()) {
                providers.put(plugin.id(), plugin.getClass().getName());
            }
            System.out.println("Distribution plug-in directory: " + directory.directory());
            System.out.println("Distribution providers: " + providers);
            var missing = new TreeSet<>(REQUIRED_PROVIDERS);
            missing.removeAll(providers.keySet());
            check(missing.isEmpty(), "Missing distribution plug-in providers: " + missing);
            System.out.println("PASS: runtime loader discovered all six Phase 2 providers.");
        }
    }

    private static JFrame openMainWindow() throws Exception {
        Life.main(new String[0]);
        return await("Life.main did not open a desktop window", () -> edt(() ->
                Arrays.stream(Window.getWindows()).filter(JFrame.class::isInstance)
                        .map(JFrame.class::cast).filter(Window::isShowing)
                        .filter(candidate -> findItem(candidate, "Grid", "Exit") != null)
                        .findFirst().orElse(null)));
    }

    private static void closeWindows() throws Exception {
        edt(() -> {
            Arrays.stream(Window.getWindows()).forEach(Window::dispose);
            return null;
        });
    }

    private static void desktop() throws Exception {
        check(!GraphicsEnvironment.isHeadless(), "desktop requires a display such as Xvfb");
        Path directory = Files.createTempDirectory("life-acceptance-");
        Path pattern = directory.resolve("roundtrip.life");
        try {
            JFrame frame = openMainWindow();
            System.out.println("Desktop menu inventory: " + edt(() -> menus(frame)));
            check(edt(() -> displayedGeneration(frame)) == 0, "Expected a new game at generation zero");

            // A single cell lights up and dies on one otherwise empty bounded board.
            click(frame, 20, 22);
            await("Click did not paint a live cell", () -> changed(frame, 20, 22) ? true : null);
            menu(frame, "Go", "Tick (Single Step)");
            await("Single Step did not advance once and remove the isolated cell",
                    () -> edt(() -> displayedGeneration(frame)) == 1 && !changed(frame, 20, 22) ? true : null);

            // A still-life block stays stable even if a tick happens during the file chooser.
            for (int y = 22; y <= 23; y++) {
                for (int x = 20; x <= 21; x++) {
                    click(frame, x, y);
                }
            }
            await("Still life was not painted", () -> blockMatches(frame, true) ? true : null);
            for (String preset : new String[]{"Agonizing", "Slow", "Medium", "Fast"}) {
                startAndObserve(frame, preset);
                menu(frame, "Go", "Halt");
                expectHalted(frame, "Halt after " + preset);
            }
            startAndObserve(frame, "Medium");
            chooseFile(frame, pattern, "Grid", "Store");
            await("Store did not write the pattern", () ->
                    Files.isRegularFile(pattern) && Files.size(pattern) > 0 && edt(frame::isEnabled) ? true : null);
            expectHalted(frame, "Store");

            menu(frame, "Grid", "Clear");
            await("Clear did not remove the block", () -> blockMatches(frame, false) ? true : null);
            startAndObserve(frame, "Medium");
            chooseFile(frame, pattern, "Grid", "Load");
            await("Load did not restore the visible block", () ->
                    edt(frame::isEnabled) && blockMatches(frame, true) ? true : null);
            expectHalted(frame, "Load");

            menu(frame, "Grid", "Exit");
            await("Exit did not close the window", () -> edt(frame::isDisplayable) ? null : true);
            System.out.println("PASS: real Life.main, click, single step, speed controls, halt, clear, .life round trip and exit.");
        } finally {
            closeWindows();
            Files.deleteIfExists(pattern);
            Files.delete(directory);
        }
    }

    private static void plugins() throws Exception {
        check(!GraphicsEnvironment.isHeadless(), "plugins requires a display such as Xvfb");
        Path directory = Files.createTempDirectory("life-plugin-acceptance-");
        Path checkpoint = directory.resolve("checkpoint.json");
        Path restored = directory.resolve("restored.json");
        try {
            JFrame frame = openMainWindow();
            System.out.println("Desktop menu inventory: " + edt(() -> menus(frame)));
            for (String id : new TreeSet<>(REQUIRED_PROVIDERS)) {
                check(edt(() -> checkboxMatches(frame, id, false)), "Expected " + id + " initially off");
            }
            check(!edt(() -> itemIsVisible(frame, "Grid", "Session files")),
                    "The session codec starts off with its menu hidden");

            menu(frame, "Go", "Tick (Single Step)");
            await("Could not advance to generation 1",
                    () -> edt(() -> displayedGeneration(frame)) == 1 ? true : null);
            click(frame, 20, 22);
            await("Could not edit the checkpoint cell", () -> changed(frame, 20, 22) ? true : null);

            for (String id : new String[]{"toroidal", "foodaging", "obstacle"}) {
                toggle(frame, id, true);
                check(edt(() -> displayedGeneration(frame)) == 1 && changed(frame, 20, 22),
                        id + " toggle changed the board or generation");
            }
            await("Obstacle did not expose its input tool menu",
                    () -> edt(() -> itemIsVisible(frame, "Tools", "obstacle:toggle-wall")) ? true : null);

            toggle(frame, "view2d", true);
            await("Enabling view2d did not show a secondary window", () -> hasPeerWindow(frame) ? true : null);
            toggle(frame, "view2d", false);
            await("Disabling view2d did not close its window", () -> hasPeerWindow(frame) ? null : true);

            toggle(frame, "codec", true);
            for (String format : new String[]{"codec:json", "codec:binary"}) {
                for (String action : new String[]{"Save session…", "Load session…"}) {
                    await("Missing session " + format + " action: " + action, () ->
                            edt(() -> itemIsVisible(frame, "Grid", "Session files", format, action)) ? true : null);
                }
            }
            saveJson(frame, checkpoint);
            check(Files.readString(checkpoint).contains("toroidal:wrap"),
                    "The checkpoint did not record the toroidal accessor");

            for (String id : new String[]{"obstacle", "foodaging", "toroidal"}) {
                toggle(frame, id, false);
            }
            menu(frame, "Grid", "Clear");
            await("Clear did not remove the checkpoint cell", () -> changed(frame, 20, 22) ? null : true);
            menu(frame, "Go", "Tick (Single Step)");
            await("Could not move beyond the checkpoint",
                    () -> edt(() -> displayedGeneration(frame)) == 2 ? true : null);

            chooseFile(frame, checkpoint, "Grid", "Session files", "codec:json", "Load session…");
            await("JSON load did not restore the visible board, generation, and plug-in plan", () ->
                    edt(() -> frame.isEnabled() && displayedGeneration(frame) == 1
                        && checkboxMatches(frame, "toroidal", true)
                        && checkboxMatches(frame, "foodaging", true)
                        && checkboxMatches(frame, "obstacle", true))
                    && changed(frame, 20, 22) ? true : null);
            saveJson(frame, restored);
            check(Files.mismatch(checkpoint, restored) == -1,
                    "Re-saving the restored session changed the serialized board or plan");

            menu(frame, "Grid", "Exit");
            await("Exit did not close the window", () -> edt(frame::isDisplayable) ? null : true);
            System.out.println("PASS: default-off plug-ins, board-preserving switches, 2D view lifecycle, "
                    + "codec menus, JSON session and plan round trip.");
        } finally {
            closeWindows();
            Files.deleteIfExists(checkpoint);
            Files.deleteIfExists(restored);
            Files.delete(directory);
        }
    }

    private static void saveJson(JFrame frame, Path file) throws Exception {
        chooseFile(frame, file, "Grid", "Session files", "codec:json", "Save session…");
        await("JSON save did not complete", () ->
                Files.isRegularFile(file) && Files.size(file) > 0 && edt(frame::isEnabled) ? true : null);
    }

    private static void toggle(JFrame frame, String id, boolean enabled) throws Exception {
        menu(frame, "Plugins", id);
        // Swing selects the box optimistically, then disables it until the host answers.
        await("Host did not accept the " + id + " switch to " + enabled, () ->
                edt(() -> checkboxMatches(frame, id, enabled)) ? true : null);
    }

    private static boolean checkboxMatches(JFrame frame, String id, boolean enabled) {
        return findItem(frame, "Plugins", id) instanceof JCheckBoxMenuItem box
                && box.isEnabled() && box.isSelected() == enabled;
    }

    private static boolean hasPeerWindow(JFrame main) throws Exception {
        return edt(() -> Arrays.stream(Window.getWindows())
                .anyMatch(window -> window != main && window instanceof JFrame && window.isShowing()));
    }

    private static void startAndObserve(JFrame frame, String preset) throws Exception {
        long before = edt(() -> displayedGeneration(frame));
        menu(frame, "Go", preset);
        await(preset + " did not begin autonomous ticks",
                () -> edt(() -> displayedGeneration(frame)) > before ? true : null);
    }

    private static void expectHalted(JFrame frame, String action) throws Exception {
        // Allow any displayed update already in the EDT queue to settle.
        Thread.sleep(200);
        long pausedGeneration = edt(() -> displayedGeneration(frame));
        Thread.sleep(650);
        check(pausedGeneration >= 0 && edt(() -> displayedGeneration(frame)) == pausedGeneration,
                action + " did not stop autonomous ticks");
    }

    private static void chooseFile(JFrame frame, Path file, String... path) throws Exception {
        JMenuItem command = edt(() -> requireItem(frame, path));
        SwingUtilities.invokeLater(() -> command.doClick(0));
        await(String.join(" > ", path) + " did not open the file chooser", () -> edt(() -> {
            for (Window owned : frame.getOwnedWindows()) {
                JFileChooser chooser = owned.isShowing() ? find(owned, JFileChooser.class) : null;
                if (chooser != null) {
                    chooser.setSelectedFile(file.toFile());
                    chooser.approveSelection();
                    return true;
                }
            }
            return null;
        }));
    }

    private static long displayedGeneration(JFrame frame) {
        var content = frame.getContentPane();
        var layout = (BorderLayout) content.getLayout();
        var label = (JLabel) layout.getLayoutComponent(BorderLayout.SOUTH);
        String text = label.getText();
        return text.startsWith(GENERATION_PREFIX) ? Long.parseLong(text.substring(GENERATION_PREFIX.length())) : -1;
    }

    private static void click(JFrame frame, int x, int y) throws Exception {
        edt(() -> {
            JPanel board = board(frame);
            Point target = sample(board, x, y);
            board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    0, target.x, target.y, 1, false, MouseEvent.BUTTON1));
            return null;
        });
    }

    private static boolean changed(JFrame frame, int x, int y) throws Exception {
        return edt(() -> {
            JPanel board = board(frame);
            BufferedImage image = paint(board);
            return color(image, sample(board, x, y)) != color(image, sample(board, 0, 0));
        });
    }

    private static boolean blockMatches(JFrame frame, boolean alive) throws Exception {
        return edt(() -> {
            JPanel board = board(frame);
            BufferedImage image = paint(board);
            int dead = color(image, sample(board, 0, 0));
            for (int y = 22; y <= 23; y++) {
                for (int x = 20; x <= 21; x++) {
                    if ((color(image, sample(board, x, y)) != dead) != alive) {
                        return false;
                    }
                }
            }
            return true;
        });
    }

    private static BufferedImage paint(JPanel board) {
        var image = new BufferedImage(board.getWidth(), board.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            board.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static int color(BufferedImage image, Point sample) {
        return image.getRGB(sample.x, sample.y);
    }

    private static Point sample(JPanel board, int x, int y) {
        int side = Math.min(board.getWidth(), board.getHeight());
        return new Point((board.getWidth() - side) / 2 + (int) ((x + 0.5) * side / BOARD_SIZE),
                (board.getHeight() - side) / 2 + (int) ((y + 0.5) * side / BOARD_SIZE));
    }

    private static JPanel board(JFrame frame) {
        var content = frame.getContentPane();
        return (JPanel) ((BorderLayout) content.getLayout()).getLayoutComponent(BorderLayout.CENTER);
    }

    private static void menu(JFrame frame, String... path) throws Exception {
        edt(() -> {
            requireItem(frame, path).doClick(0);
            return null;
        });
    }

    private static JMenuItem requireItem(JFrame frame, String... path) {
        JMenuItem command = findItem(frame, path);
        check(command != null && command.isVisible() && command.isEnabled(),
                "Missing or unavailable menu action: " + String.join(" > ", path));
        return command;
    }

    private static boolean itemIsVisible(JFrame frame, String... path) {
        JMenuItem item = findItem(frame, path);
        return item != null && item.isVisible();
    }

    private static JMenuItem findItem(JFrame frame, String... path) {
        var bar = frame.getJMenuBar();
        if (bar == null || path.length == 0) return null;
        JMenuItem current = null;
        for (int index = 0; index < bar.getMenuCount(); index++) {
            var menu = bar.getMenu(index);
            if (menu != null && path[0].equals(menu.getText())) {
                current = menu;
                break;
            }
        }
        for (int index = 1; index < path.length; index++) {
            if (!(current instanceof JMenu menu) || !menu.isVisible()) return null;
            current = null;
            for (int childIndex = 0; childIndex < menu.getItemCount(); childIndex++) {
                JMenuItem child = menu.getItem(childIndex);
                if (child != null && path[index].equals(child.getText())) {
                    current = child;
                    break;
                }
            }
        }
        return current;
    }

    private static String menus(JFrame frame) {
        var bar = frame.getJMenuBar();
        var groups = new java.util.LinkedHashMap<String, java.util.List<String>>();
        for (int index = 0; index < bar.getMenuCount(); index++) {
            var group = bar.getMenu(index);
            if (group != null) {
                var names = new java.util.ArrayList<String>();
                for (int item = 0; item < group.getItemCount(); item++) {
                    if (group.getItem(item) != null) {
                        names.add(group.getItem(item).getText());
                    }
                }
                groups.put(group.getText(), names);
            }
        }
        return groups.toString();
    }

    private static <T extends Component> T find(Component component, Class<T> kind) {
        if (kind.isInstance(component)) return kind.cast(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, kind);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static <T> T await(String failure, Callable<T> action) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(6);
        do {
            T result = action.call();
            if (result != null) return result;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError(failure);
    }

    private static void check(boolean condition, String failure) {
        if (!condition) throw new AssertionError(failure);
    }
}
