package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import rpg.content.B16ContentLoader;
import rpg.content.ContentSnapshot;
import rpg.content.validation.ContentInvariantViolation;
import rpg.core.classes.ClassConfig;
import rpg.core.classes.ClassConfigSchema;
import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.zone.WorldResolver;
import rpg.platform.config.YamlConfigLoader;

/** T016: the real parser and all nine shipped defaults produce one complete content snapshot. */
class B16ContentSnapshotTest {

    private static final List<String> SOURCES =
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

    @Test
    void shippedDefaultsAreParsedAndCapturedAsOneGeneration(@TempDir Path directory) throws Exception {
        copyShippedDefaults(directory);

        ContentSnapshot snapshot = loadSnapshot(directory);

        assertThat(snapshot.documents()).extracting(document -> document.source()).containsExactlyElementsOf(SOURCES);
        assertThat(snapshot.size()).isEqualTo(9);
        assertThat(snapshot.requireByDomain("classes").schemaVersion()).isEqualTo(1);
        assertThat(snapshot.requireByDomain("mobs").registry().size()).isGreaterThan(0);
        assertThat(snapshot.references()).isNotEmpty();
    }

    @Test
    void aValidReloadPublishesOrdinaryAndBatchHandlesTogether(@TempDir Path directory)
            throws Exception {
        copyShippedDefaults(directory);

        YamlConfigLoader yamlLoader = new YamlConfigLoader(directory);
        B16ContentLoader.Sources sources = B16ContentLoader.Sources.standard();
        B16ContentLoader contentLoader =
                new B16ContentLoader(
                        yamlLoader::readDocument,
                        WorldResolver.of(Map.of("world", UUID.randomUUID())));
        ConfigHandle<ClassConfig> classes =
                yamlLoader.register(Path.of("classes.yml"), ClassConfigSchema.schema());
        ConfigHandle<ContentSnapshot> snapshot =
                yamlLoader.registerBatch(sources.orderedPaths(), () -> contentLoader.loadSnapshot(sources));
        ClassConfig previousClasses = classes.get();
        ContentSnapshot previousSnapshot = snapshot.get();

        assertThat(warriorHealth(previousClasses)).isEqualTo(40.0);
        assertThat(warriorHealth(previousSnapshot)).isEqualTo(40.0);

        String classesYaml = Files.readString(directory.resolve("classes.yml"));
        assertThat(classesYaml).contains("      health: 40.0");
        Files.writeString(
                directory.resolve("classes.yml"),
                classesYaml.replace("      health: 40.0", "      health: 41.0")
                        + "\n# second valid generation\n");

        yamlLoader.reloadAll();

        assertThat(classes.get()).isNotSameAs(previousClasses);
        assertThat(snapshot.get()).isNotSameAs(previousSnapshot);
        assertThat(warriorHealth(classes.get())).isEqualTo(41.0);
        assertThat(warriorHealth(snapshot.get())).isEqualTo(41.0);
        assertThat(warriorHealth(previousClasses)).isEqualTo(40.0);
        assertThat(warriorHealth(previousSnapshot)).isEqualTo(40.0);
        assertThat(snapshot.get().documents()).extracting(document -> document.source())
                .containsExactlyElementsOf(SOURCES);
    }

    @Test
    void anInvalidSingleFileDoesNotPublishAnyReplacement(@TempDir Path directory)
            throws Exception {
        copyShippedDefaults(directory);

        YamlConfigLoader yamlLoader = new YamlConfigLoader(directory);
        B16ContentLoader.Sources sources = B16ContentLoader.Sources.standard();
        B16ContentLoader contentLoader =
                new B16ContentLoader(
                        yamlLoader::readDocument,
                        WorldResolver.of(Map.of("world", UUID.randomUUID())));
        ConfigHandle<ClassConfig> classes =
                yamlLoader.register(Path.of("classes.yml"), ClassConfigSchema.schema());
        ConfigHandle<ContentSnapshot> snapshot =
                yamlLoader.registerBatch(sources.orderedPaths(), () -> contentLoader.loadSnapshot(sources));
        ClassConfig previousClasses = classes.get();
        ContentSnapshot previousSnapshot = snapshot.get();

        Files.writeString(
                directory.resolve("classes.yml"),
                Files.readString(directory.resolve("classes.yml"))
                        .replaceFirst("schemaVersion: 1", "schemaVersion: 99"));

        assertThatThrownBy(yamlLoader::reloadAll)
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("classes.yml");
        assertThat(classes.get()).isSameAs(previousClasses);
        assertThat(snapshot.get()).isSameAs(previousSnapshot);
    }

    @Test
    void aCrossDomainFailureRollsBackEveryObservedState(@TempDir Path directory)
            throws Exception {
        copyShippedDefaults(directory);

        YamlConfigLoader yamlLoader = new YamlConfigLoader(directory);
        B16ContentLoader.Sources sources = B16ContentLoader.Sources.standard();
        B16ContentLoader contentLoader =
                new B16ContentLoader(
                        yamlLoader::readDocument,
                        WorldResolver.of(Map.of("world", UUID.randomUUID())));
        ConfigHandle<ClassConfig> classes =
                yamlLoader.register(Path.of("classes.yml"), ClassConfigSchema.schema());
        ConfigHandle<ContentSnapshot> snapshot =
                yamlLoader.registerBatch(sources.orderedPaths(), () -> contentLoader.loadSnapshot(sources));
        ClassConfig previousClasses = classes.get();
        ContentSnapshot previousSnapshot = snapshot.get();
        List<String> previousSources =
                previousSnapshot.documents().stream().map(document -> document.source()).toList();
        int previousReferenceCount = previousSnapshot.references().size();

        String brokenMobs =
                Files.readString(directory.resolve("mobs.yml"))
                        .replace("area: 'greenfields-east'", "area: 'missing-area'");
        Files.writeString(directory.resolve("mobs.yml"), brokenMobs);

        ContentInvariantViolation violation = null;
        try {
            yamlLoader.reloadAll();
        } catch (ContentInvariantViolation caught) {
            violation = caught;
        }

        assertThat(violation).isNotNull();
        assertThat(violation.sourceDomain()).isEqualTo("mobs");
        assertThat(violation.sourceId()).isEqualTo("hordes.greenfields.boss");
        assertThat(violation.targetDomain()).isEqualTo("zones");
        assertThat(violation.targetId()).isEqualTo("missing-area");
        assertThat(classes.get()).isSameAs(previousClasses);
        assertThat(snapshot.get()).isSameAs(previousSnapshot);
        assertThat(snapshot.get().documents()).extracting(document -> document.source())
                .containsExactlyElementsOf(previousSources);
        assertThat(snapshot.get().references()).hasSize(previousReferenceCount);
    }

    private static double warriorHealth(ClassConfig config) {
        return config.definition(CharacterClass.WARRIOR).baseStats().of(Attribute.HEALTH);
    }

    private static double warriorHealth(ContentSnapshot snapshot) {
        return warriorHealth((ClassConfig) snapshot.requireByDomain("classes").content());
    }

    private static ContentSnapshot loadSnapshot(Path directory) throws Exception {
        YamlConfigLoader yamlLoader = new YamlConfigLoader(directory);
        B16ContentLoader.Sources sources = B16ContentLoader.Sources.standard();
        return new B16ContentLoader(
                        yamlLoader::readDocument,
                        WorldResolver.of(Map.of("world", UUID.randomUUID())))
                .loadSnapshot(sources);
    }

    private static void copyShippedDefaults(Path directory) throws Exception {
        for (String source : SOURCES) {
            try (InputStream resource = B16ContentSnapshotTest.class.getResourceAsStream("/" + source)) {
                assertThat(resource).as("classpath resource %s", source).isNotNull();
                Files.copy(resource, directory.resolve(source));
            }
        }
    }
}
