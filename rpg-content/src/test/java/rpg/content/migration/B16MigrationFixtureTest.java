package rpg.content.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class B16MigrationFixtureTest {

    private static final List<String> B16_FILES =
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
    @DisplayName("golden fixtures preserve selected B07-B11 values through v0-to-v1")
    void goldenFixturesKeepValuesAndProduceTheExpectedEnvelope() throws Exception {
        Path fixtureRoot = fixtureRoot();
        B16MigrationPlan plan = B16MigrationPlan.standard();
        Yaml yaml = new Yaml();

        for (String fileName : B16_FILES) {
            Path legacyPath = fixtureRoot.resolve("legacy").resolve(fileName);
            Path expectedPath = fixtureRoot.resolve("expected").resolve(fileName);
            Map<String, Object> legacy = load(yaml, legacyPath);
            Map<String, Object> expected = load(yaml, expectedPath);

            B16MigrationPlan.MigrationResult result =
                    plan.migrate(Path.of(fileName), legacy);

            assertThat(result.changed()).as(fileName).isTrue();
            assertThat(result.document()).as(fileName).isEqualTo(expected);
            assertThat(withoutSchemaVersion(result.document()))
                    .as(fileName + " values")
                    .isEqualTo(legacy);
            assertThat(expected).containsEntry("schemaVersion", 1);
            assertThat(Files.readString(legacyPath)).doesNotContain("schemaVersion:");
        }
    }

    private static Path fixtureRoot() {
        Path fromRoot = Path.of("tools", "b16-migration", "fixtures");
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }
        return Path.of("..", "tools", "b16-migration", "fixtures");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(Yaml yaml, Path path) throws IOException {
        Object parsed = yaml.load(Files.readString(path));
        assertThat(parsed).as(path.toString()).isInstanceOf(Map.class);
        return (Map<String, Object>) parsed;
    }

    private static Map<String, Object> withoutSchemaVersion(Map<String, Object> document) {
        Map<String, Object> copy = new LinkedHashMap<>(document);
        copy.remove("schemaVersion");
        return copy;
    }
}
