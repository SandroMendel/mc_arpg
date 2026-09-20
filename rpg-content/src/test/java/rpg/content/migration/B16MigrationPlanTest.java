package rpg.content.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class B16MigrationPlanTest {

    private static final Path CLASSES = Path.of("classes.yml");

    @Test
    @DisplayName("the registered v0 step adds only the version envelope")
    void legacyDocumentGetsVersionWithoutChangingValuesOrOrder() throws Exception {
        Map<String, Object> classes =
                new LinkedHashMap<>(
                        Map.of(
                                "classes",
                                        Map.of(
                                                "warrior",
                                                Map.of(
                                                        "base-stats", Map.of("health", 100.0),
                                                        "abilities", List.of("slash")))));

        B16MigrationPlan.MigrationResult result =
                B16MigrationPlan.standard().migrate(CLASSES, classes);

        assertThat(result.changed()).isTrue();
        assertThat(result.document().keySet()).containsExactly("schemaVersion", "classes");
        assertThat(result.document().get("schemaVersion")).isEqualTo(1);
        assertThat(result.document().get("classes")).isEqualTo(classes.get("classes"));
    }

    @Test
    @DisplayName("all nine B16 files have one explicit v0-to-v1 step")
    void standardPlanIsRegisteredInCanonicalOrder() {
        assertThat(B16MigrationPlan.standard().steps())
                .extracting(MigrationStep::fromVersion)
                .containsOnly(0);
        assertThat(B16MigrationPlan.standard().steps())
                .extracting(MigrationStep::toVersion)
                .containsOnly(1);
        assertThat(B16MigrationPlan.standard().steps())
                .extracting(step -> ((LegacyV0ToV1Step) step).fileName())
                .containsExactly(
                        "classes.yml",
                        "abilities.yml",
                        "progression.yml",
                        "combat.yml",
                        "zones.yml",
                        "mobs.yml",
                        "items.yml",
                        "currency.yml",
                        "stats.yml");
    }

    @Test
    @DisplayName("unknown root sections are not guessed into a migration")
    void unknownLegacyRootSectionIsRejectedWithItsPath() {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("abilities", Map.of());
        legacy.put("future-balance", Map.of("value", 9));

        assertThatThrownBy(() -> B16MigrationPlan.standard().migrate(Path.of("abilities.yml"), legacy))
                .isInstanceOf(B16MigrationException.class)
                .hasMessageContaining("abilities.yml")
                .hasMessageContaining("future-balance");
    }

    @Test
    @DisplayName("already versioned input is an unchanged, repeatable no-op")
    void versionOneIsNotMigratedAgain() throws Exception {
        Map<String, Object> current =
                new LinkedHashMap<>(Map.of("schemaVersion", 1, "currency", Map.of("account", Map.of())));

        B16MigrationPlan.MigrationResult result =
                B16MigrationPlan.standard().migrate(Path.of("currency.yml"), current);

        assertThat(result.changed()).isFalse();
        assertThat(result.document()).isEqualTo(current);
    }

    @Test
    @DisplayName("an explicit legacy version is not treated as an unversioned file")
    void explicitZeroIsRejectedRatherThanGuessed() {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("schemaVersion", 0);
        legacy.put("stats", Map.of());

        assertThatThrownBy(() -> B16MigrationPlan.standard().migrate(Path.of("stats.yml"), legacy))
                .isInstanceOf(B16MigrationException.class)
                .hasMessageContaining("schemaVersion")
                .hasMessageContaining("exact integer schemaVersion 1");
    }
}
