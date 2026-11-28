package com.holub.life.host;

import com.holub.life.api.LifePlugin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.jar.JarFile;

/** Discovers plug-in services in a directory for the lifetime of one application session. */
public final class PluginDirectory implements AutoCloseable {
    private static final String DIRECTORY_PROPERTY = "life.plugins.dir";
    private static final String API_PACKAGE_PREFIX = "com.holub.life.api.";

    private final Path directory;
    private final URLClassLoader loader;
    private final List<LifePlugin> plugins;

    private PluginDirectory(Path directory, URLClassLoader loader, List<LifePlugin> plugins) {
        this.directory = directory;
        this.loader = loader;
        this.plugins = List.copyOf(plugins);
    }

    public static PluginDirectory open() throws IOException {
        String override = System.getProperty(DIRECTORY_PROPERTY);
        if (override != null) {
            if (override.isBlank()) {
                throw new IOException(DIRECTORY_PROPERTY + " must name a plug-in directory");
            }
            return open(Path.of(override));
        }
        return open(defaultDirectory());
    }

    public static PluginDirectory open(Path path) throws IOException {
        Path directory = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (Files.notExists(directory)) {
            return new PluginDirectory(directory, null, List.of());
        }
        if (!Files.isDirectory(directory)) {
            throw new IOException("Plug-in path is not a directory: " + directory);
        }

        List<Path> jars;
        try (var files = Files.list(directory)) {
            jars = files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .toList();
        } catch (IOException | java.io.UncheckedIOException failure) {
            throw new IOException("Could not list the plug-in directory: " + directory, failure);
        }
        if (jars.isEmpty()) {
            return new PluginDirectory(directory, null, List.of());
        }

        var urls = new ArrayList<URL>(jars.size());
        for (Path jar : jars) {
            try (var archive = new JarFile(jar.toFile())) {
                // Opening the archive detects broken jars instead of quietly skipping their services.
                urls.add(Path.of(archive.getName()).toUri().toURL());
            } catch (IOException failure) {
                throw new IOException("Could not open plug-in JAR " + jar, failure);
            }
        }

        var loader = new URLClassLoader("life-plugins", urls.toArray(URL[]::new), sharedApiParent());
        Thread current = Thread.currentThread();
        ClassLoader previous = current.getContextClassLoader();
        try {
            current.setContextClassLoader(loader);
            var loaded = new ArrayList<LifePlugin>();
            var ids = new HashSet<String>();
            for (LifePlugin plugin : ServiceLoader.load(LifePlugin.class, loader)) {
                String id = plugin.id();
                if (id == null || id.isBlank()) {
                    throw new IllegalArgumentException("Missing ID for " + plugin.getClass().getName());
                }
                if (!ids.add(id)) {
                    throw new IllegalArgumentException("Duplicate plug-in ID: " + id);
                }
                loaded.add(plugin);
            }
            return new PluginDirectory(directory, loader, loaded);
        } catch (ServiceConfigurationError | LinkageError | RuntimeException failure) {
            var message = new IOException("Could not discover plug-ins in " + directory + ": " + failure, failure);
            try {
                loader.close();
            } catch (IOException closeFailure) {
                message.addSuppressed(closeFailure);
            }
            throw message;
        } finally {
            current.setContextClassLoader(previous);
        }
    }

    public Path directory() {
        return directory;
    }

    public List<LifePlugin> plugins() {
        return plugins;
    }

    /** Context loader for plug-ins that discover native backends or service resources. */
    public ClassLoader classLoader() {
        return loader == null ? LifePlugin.class.getClassLoader() : loader;
    }

    @Override
    public void close() throws IOException {
        if (loader != null) {
            loader.close();
        }
    }

    private static Path defaultDirectory() throws IOException {
        var source = PluginDirectory.class.getProtectionDomain().getCodeSource();
        if (source != null && "file".equals(source.getLocation().getProtocol())) {
            try {
                Path executable = Path.of(source.getLocation().toURI()).toAbsolutePath();
                if (Files.isRegularFile(executable)) {
                    Path parent = executable.getParent();
                    Path adjacent = parent.resolve("plugins");
                    if (!Files.exists(adjacent) && "lib".equals(parent.getFileName().toString())) {
                        // Gradle installation: bin/<launcher>, lib/<host>.jar, plugins/*.jar.
                        return parent.resolveSibling("plugins");
                    }
                    return adjacent;
                }
            } catch (URISyntaxException failure) {
                throw new IOException("Could not locate the application JAR: " + source.getLocation(), failure);
            }
        }
        return Path.of("plugins");
    }

    private static ClassLoader sharedApiParent() {
        var api = LifePlugin.class.getClassLoader();
        // Java platform classes and the shared API come from the parent. Every other class
        // must come from the plug-in directory, even if it exists on the host's test classpath.
        return new ClassLoader(ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith(API_PACKAGE_PREFIX)) {
                    return api.loadClass(name);
                }
                return super.loadClass(name, resolve);
            }
        };
    }
}
