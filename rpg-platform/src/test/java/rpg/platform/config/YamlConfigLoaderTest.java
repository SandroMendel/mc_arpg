package rpg.platform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.FieldType;

/**
 * T019 / T037: the shipping YAML loader against real files.
 *
 * <p>The validation, reload and rollback rules themselves are covered in {@code rpg-core}; this test
 * confirms the YAML parsing step feeds them correctly and that the inherited rollback really does
 * apply to the loader that ships (quickstart section 3).
 */
class YamlConfigLoaderTest {

    private record CombatConfig(String formula, int maxTargets) {}

    private static ConfigSchema<CombatConfig> schema() {
        return ConfigSchema.<CombatConfig>builder(1)
                .required("combat.damage-formula", FieldType.STRING)
                .required("combat.max-targets", FieldType.INTEGER)
                .boundTo(
                        view ->
                                new CombatConfig(
                                        view.getString("combat.damage-formula"),
                                        view.getInt("combat.max-targets")))
                .build();
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content);
    }

    @Test
    void aValidYamlFileIsLoaded(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("combat.yml");
        write(
                file,
                """
                combat:
                  damage-formula: 'atk * 1.2'
                  max-targets: 5
                """);

        CombatConfig config = new YamlConfigLoader(dir).loadAndValidate(Path.of("combat.yml"), schema());

        assertThat(config).isEqualTo(new CombatConfig("atk * 1.2", 5));
    }

    @Test
    void aMissingFileIsReportedWithItsPath(@TempDir Path dir) {
        YamlConfigLoader loader = new YamlConfigLoader(dir);

        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () -> loader.loadAndValidate(Path.of("absent.yml"), schema()));

        assertThat(thrown).hasMessageContaining("absent.yml");
    }

    @Test
    void malformedYamlIsRejectedWithAClearMessage(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("combat.yml");
        write(file, "combat:\n  damage-formula: 'unterminated\n    max-targets: 5\n");

        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () -> new YamlConfigLoader(dir).loadAndValidate(Path.of("combat.yml"), schema()));

        assertThat(thrown).isNotNull();
        assertThat(thrown.expected()).contains("YAML");
    }

    @Test
    void aMissingRequiredKeyNamesFilePathAndExpectedValue(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("combat.yml");
        write(file, "combat:\n  damage-formula: 'atk'\n");

        ConfigValidationException thrown =
                catchThrowableOfType(
                        ConfigValidationException.class,
                        () -> new YamlConfigLoader(dir).loadAndValidate(Path.of("combat.yml"), schema()));

        assertThat(thrown.documentPath()).isEqualTo("combat.max-targets");
        assertThat(thrown.expected()).contains("integer").contains("required");
        assertThat(thrown.actual()).contains("missing");
    }

    @Test
    void aDuplicatedKeyIsRejectedRatherThanSilentlyMerged(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("combat.yml");
        write(
                file,
                """
                combat:
                  damage-formula: 'atk'
                  max-targets: 5
                  max-targets: 9
                """);

        assertThat(
                        catchThrowableOfType(
                                ConfigValidationException.class,
                                () ->
                                        new YamlConfigLoader(dir)
                                                .loadAndValidate(Path.of("combat.yml"), schema())))
                .isNotNull();
    }

    @Test
    void aReloadAppliesTheEditedFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("combat.yml");
        write(file, "combat:\n  damage-formula: 'atk'\n  max-targets: 5\n");

        YamlConfigLoader loader = new YamlConfigLoader(dir);
        ConfigHandle<CombatConfig> handle = loader.register(Path.of("combat.yml"), schema());
        assertThat(handle.get().maxTargets()).isEqualTo(5);

        write(file, "combat:\n  damage-formula: 'atk'\n  max-targets: 9\n");
        loader.reloadAll();

        assertThat(handle.get().maxTargets()).isEqualTo(9);
    }

    @Test
    void aBrokenReloadKeepsThePreviouslyValidConfiguration(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("combat.yml");
        write(file, "combat:\n  damage-formula: 'atk'\n  max-targets: 5\n");

        YamlConfigLoader loader = new YamlConfigLoader(dir);
        ConfigHandle<CombatConfig> handle = loader.register(Path.of("combat.yml"), schema());

        write(file, "combat:\n  damage-formula: 'atk'\n  max-targets: 'plenty'\n");

        assertThat(catchThrowableOfType(ConfigValidationException.class, loader::reloadAll))
                .isNotNull();
        assertThat(handle.get().maxTargets()).isEqualTo(5);

        // and the operator can recover without a restart
        write(file, "combat:\n  damage-formula: 'atk'\n  max-targets: 7\n");
        assertThatCode(loader::reloadAll).doesNotThrowAnyException();
        assertThat(handle.get().maxTargets()).isEqualTo(7);
    }

    @Test
    void anAbsolutePathBypassesTheBaseDirectory(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("combat.yml");
        write(file, "combat:\n  damage-formula: 'atk'\n  max-targets: 3\n");

        CombatConfig config =
                new YamlConfigLoader(Path.of("some", "other", "place"))
                        .loadAndValidate(file.toAbsolutePath(), schema());

        assertThat(config.maxTargets()).isEqualTo(3);
    }
}
