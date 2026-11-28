package com.holub.life.host;

import com.holub.life.api.LifePlugin;
import com.holub.life.host.fixture.LooseFixturePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDirectoryTest {
    private static final String SERVICE_FILE = "META-INF/services/" + LifePlugin.class.getName();
    private static final String PROPERTY = "life.plugins.dir";

    @TempDir
    Path temporary;

    @Test
    void missingDirectoryAndNonJarFilesDoNotPreventCoreStartup() throws IOException {
        Path missing = temporary.resolve("optional");
        try (PluginDirectory found = PluginDirectory.open(missing)) {
            assertEquals(missing, found.directory());
            assertTrue(found.plugins().isEmpty());
        }
        Files.writeString(temporary.resolve("README.txt"), "Place plug-in JARs in this directory");
        try (PluginDirectory found = PluginDirectory.open(temporary)) {
            assertTrue(found.plugins().isEmpty());
        }
    }

    @Test
    void looseProviderResolvesItsSeparateJarDependencyAndSharesTheHostApi() throws IOException {
        writeProvider(temporary.resolve("provider.jar"));
        writeJar(temporary.resolve("dependency.jar"), Map.of(
                classEntry(LooseFixturePlugin.Dependency.class), classBytes(LooseFixturePlugin.Dependency.class),
                "loader-fixture.txt", "resource from the sibling JAR".getBytes(StandardCharsets.UTF_8)));
        try (PluginDirectory found = PluginDirectory.open(temporary)) {
            assertEquals(1, found.plugins().size());
            LifePlugin plugin = found.plugins().getFirst();
            assertEquals("test:loose-provider", plugin.id());
            assertNotSame(LooseFixturePlugin.class, plugin.getClass());
            assertSame(found.classLoader(), plugin.getClass().getClassLoader());
            try (var resource = found.classLoader().getResourceAsStream("loader-fixture.txt")) {
                assertEquals("resource from the sibling JAR",
                        new String(java.util.Objects.requireNonNull(resource).readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void missingLooseDependencyIsNotBorrowedFromTheTestClasspath() throws IOException {
        writeProvider(temporary.resolve("provider.jar"));
        var failure = assertThrows(IOException.class, () -> PluginDirectory.open(temporary));
        assertTrue(failure.getMessage().contains(temporary.toString()));
        assertTrue(failure.getMessage().contains(LooseFixturePlugin.class.getName()));
        assertInstanceOf(ServiceConfigurationError.class, failure.getCause());
    }

    @Test
    void namesTheInvalidJarAndRejectsAnExplicitNonDirectory() throws IOException {
        Path badJar = Files.writeString(temporary.resolve("broken.jar"), "Not a JAR");
        var nonDirectory = assertThrows(IOException.class, () -> PluginDirectory.open(badJar));
        assertTrue(nonDirectory.getMessage().contains("not a directory"));
        var invalidJar = assertThrows(IOException.class, () -> PluginDirectory.open(temporary));
        assertTrue(invalidJar.getMessage().contains(badJar.toString()));
        assertInstanceOf(java.util.zip.ZipException.class, invalidJar.getCause());
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void directoryOverrideWinsOverTheExecutableLocation() throws IOException {
        String original = System.getProperty(PROPERTY);
        System.setProperty(PROPERTY, temporary.toString());
        try (PluginDirectory found = PluginDirectory.open()) {
            assertEquals(temporary, found.directory());
            assertTrue(found.plugins().isEmpty());
        } finally {
            if (original == null) {
                System.clearProperty(PROPERTY);
            } else {
                System.setProperty(PROPERTY, original);
            }
        }
    }

    private static void writeProvider(Path path) throws IOException {
        writeJar(path, Map.of(classEntry(LooseFixturePlugin.class), classBytes(LooseFixturePlugin.class),
                SERVICE_FILE, (LooseFixturePlugin.class.getName() + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    private static String classEntry(Class<?> type) {
        return type.getName().replace('.', '/') + ".class";
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        try (var resource = type.getClassLoader().getResourceAsStream(classEntry(type))) {
            if (resource == null) {
                throw new IOException("Missing fixture class: " + type.getName());
            }
            return resource.readAllBytes();
        }
    }

    private static void writeJar(Path path, Map<String, byte[]> entries) throws IOException {
        try (var jar = new JarOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
    }
}
