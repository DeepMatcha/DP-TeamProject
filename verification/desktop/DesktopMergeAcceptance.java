import com.holub.life.Life;
import com.holub.life.api.LifePlugin;

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
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Release-artifact smoke checks. Both modes use the caller's real application classpath. */
public final class DesktopMergeAcceptance {
    private static final Set<String> REQUIRED_PROVIDERS = Set.of(
            "codec", "foodaging", "obstacle", "toroidal", "view2d", "view3d");
    private static final String GENERATION_PREFIX = "Generation: ";
    private static final int BOARD_SIZE = 64;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Specify desktop or providers");
        }
        switch (arguments[0]) {
            case "desktop" -> desktop();
            case "providers" -> providers();
            default -> throw new IllegalArgumentException("Unknown acceptance mode: " + arguments[0]);
        }
    }

    private static void providers() {
        Map<String, String> providers = new TreeMap<>();
        for (LifePlugin plugin : ServiceLoader.load(LifePlugin.class)) {
            String id = plugin.id();
            check(providers.putIfAbsent(id, plugin.getClass().getName()) == null,
                    "Duplicate plugin provider ID: " + id);
        }
        System.out.println("Distribution providers: " + providers);
        var missing = new TreeSet<>(REQUIRED_PROVIDERS);
        missing.removeAll(providers.keySet());
        check(missing.isEmpty(), "Missing distribution plugin providers: " + missing);
        System.out.println("PASS: all six Phase 2 providers are discoverable in the distribution.");
    }

    private static void desktop() throws Exception {
        check(!GraphicsEnvironment.isHeadless(), "desktop requires a display such as Xvfb");
        Path directory = Files.createTempDirectory("life-acceptance-");
        Path pattern = directory.resolve("roundtrip.life");
        try {
            Life.main(new String[0]);
            JFrame frame = await("Life.main did not open a desktop window", () -> edt(() ->
                    Arrays.stream(Window.getWindows()).filter(JFrame.class::isInstance)
                            .map(JFrame.class::cast).filter(Window::isShowing)
                            .filter(candidate -> candidate.getJMenuBar() != null)
                            .findFirst().orElse(null)));
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
            chooseFile(frame, "Store", pattern);
            await("Store did not write the pattern", () ->
                    Files.isRegularFile(pattern) && Files.size(pattern) > 0 && edt(frame::isEnabled) ? true : null);
            expectHalted(frame, "Store");

            menu(frame, "Grid", "Clear");
            await("Clear did not remove the block", () -> blockMatches(frame, false) ? true : null);
            startAndObserve(frame, "Medium");
            chooseFile(frame, "Load", pattern);
            await("Load did not restore the visible block", () ->
                    edt(frame::isEnabled) && blockMatches(frame, true) ? true : null);
            expectHalted(frame, "Load");

            menu(frame, "Grid", "Exit");
            await("Exit did not close the window", () -> edt(frame::isDisplayable) ? null : true);
            System.out.println("PASS: real Life.main, click, single step, speed controls, halt, clear, .life round trip and exit.");
        } finally {
            edt(() -> {
                Arrays.stream(Window.getWindows()).forEach(Window::dispose);
                return null;
            });
            Files.deleteIfExists(pattern);
            Files.delete(directory);
        }
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

    private static void chooseFile(JFrame frame, String item, Path file) throws Exception {
        JMenuItem command = edt(() -> findItem(frame, "Grid", item));
        SwingUtilities.invokeLater(() -> command.doClick(0));
        await(item + " did not open the file chooser", () -> edt(() -> {
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

    private static void menu(JFrame frame, String menu, String title) throws Exception {
        edt(() -> {
            findItem(frame, menu, title).doClick(0);
            return null;
        });
    }

    private static JMenuItem findItem(JFrame frame, String menu, String title) {
        var bar = frame.getJMenuBar();
        for (int index = 0; index < bar.getMenuCount(); index++) {
            var group = bar.getMenu(index);
            if (group != null && menu.equals(group.getText())) {
                for (int item = 0; item < group.getItemCount(); item++) {
                    JMenuItem command = group.getItem(item);
                    if (command != null && title.equals(command.getText())) {
                        return command;
                    }
                }
            }
        }
        throw new AssertionError("Missing menu action: " + menu + " > " + title);
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
