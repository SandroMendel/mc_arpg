package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import rpg.content.B16ContentLoader;

/** Server-free checks for B16's single classpath owner and bootstrap copy policy. */
class ContentResourceOwnershipTest {

    private static final List<String> CONTENT_DEFAULTS =
            List.of(
                    "classes.yml",
                    "abilities.yml",
                    "progression.yml",
                    "combat.yml",
                    "zones.yml",
                    "mobs.yml",
                    "items.yml",
                    "currency.yml",
                    "stats.yml");

    private static final Pattern ROOT_SCHEMA_VERSION_ONE =
            Pattern.compile("(?m)^schemaVersion:\\s*1\\s*$");

    @Test
    void everyContentDefaultIsLoadedExactlyOnceWithRootSchemaVersionOne() throws Exception {
        ClassLoader loader = ContentResourceOwnershipTest.class.getClassLoader();

        for (String file : CONTENT_DEFAULTS) {
            Enumeration<java.net.URL> candidates = loader.getResources(file);
            List<java.net.URL> resources = Collections.list(candidates);
            assertThat(resources).as(file + " must have one runtime owner").hasSize(1);

            try (InputStream stream = loader.getResourceAsStream(file)) {
                assertThat(stream).as(file + " must be loadable from the runtime classpath").isNotNull();
                String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(ROOT_SCHEMA_VERSION_ONE.matcher(yaml).results().count())
                        .as(file + " must declare root schemaVersion: 1 exactly once")
                        .isEqualTo(1);
            }
        }
    }

    @Test
    void contentDefaultsAreProvidedByTheRpgContentArtifact() throws Exception {
        String contentCodeSource =
                B16ContentLoader.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toExternalForm();
        assertThat(contentCodeSource).contains("rpg-content");

        ClassLoader loader = ContentResourceOwnershipTest.class.getClassLoader();
        for (String file : CONTENT_DEFAULTS) {
            java.net.URL resource = loader.getResource(file);
            assertThat(resource).as(file + " must come from the content artifact").isNotNull();
            assertThat(resource.toExternalForm()).contains("rpg-content");
        }
    }

    @Test
    void deployableJarContainsEachContentDefaultExactlyOnce() throws Exception {
        List<Path> jars;
        try (var files = Files.list(Path.of("build", "libs"))) {
            jars = files.filter(path -> path.getFileName().toString().endsWith(".jar")).toList();
        }
        assertThat(jars).as("the plugin jar must be built before this test").hasSize(1);

        try (JarFile jar = new JarFile(jars.get(0).toFile())) {
            for (String file : CONTENT_DEFAULTS) {
                JarEntry entry = jar.getJarEntry(file);
                assertThat(entry).as(file + " must be a root entry in the deployable jar").isNotNull();
                assertThat(jar.stream().filter(candidate -> candidate.getName().equals(file)).count())
                        .as(file + " must occur exactly once in the deployable jar")
                        .isEqualTo(1);
                try (InputStream stream = jar.getInputStream(entry)) {
                    String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    assertThat(ROOT_SCHEMA_VERSION_ONE.matcher(yaml).results().count())
                            .as(file + " in the deployable jar must declare schemaVersion: 1")
                            .isEqualTo(1);
                }
            }
        }
    }

    @Test
    void missingDefaultsAreCopiedUnderOriginalNames() throws Exception {
        Path dataFolder = Files.createTempDirectory("rpg-b16-bootstrap-missing-");
        ClassLoader loader = ContentResourceOwnershipTest.class.getClassLoader();
        List<String> saved = new java.util.ArrayList<>();

        RpgPlugin.saveMissingDefaults(
                dataFolder,
                file -> {
                    saved.add(file);
                    try (InputStream stream = loader.getResourceAsStream(file)) {
                        assertThat(stream).as(file + " must exist on the content classpath").isNotNull();
                        Files.copy(stream, dataFolder.resolve(file));
                    } catch (java.io.IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                });

        assertThat(saved).containsAll(CONTENT_DEFAULTS);
        for (String file : CONTENT_DEFAULTS) {
            Path copied = dataFolder.resolve(file);
            assertThat(copied).exists();
            String yaml = Files.readString(copied);
            assertThat(ROOT_SCHEMA_VERSION_ONE.matcher(yaml).results().count())
                    .as(file + " copied to the data folder must keep schemaVersion: 1")
                    .isEqualTo(1);
        }
    }

    @Test
    void anExistingOperatorFileRemainsByteIdentical() throws Exception {
        Path dataFolder = Files.createTempDirectory("rpg-b16-bootstrap-");
        Path existing = dataFolder.resolve("classes.yml");
        byte[] operatorBytes = "operator-owned\n".getBytes(StandardCharsets.UTF_8);
        Files.write(existing, operatorBytes);

        List<String> saved = new java.util.ArrayList<>();
        RpgPlugin.saveMissingDefaults(dataFolder, saved::add);

        assertThat(Files.readAllBytes(existing)).containsExactly(operatorBytes);
        assertThat(saved).doesNotContain("classes.yml");
    }
}
