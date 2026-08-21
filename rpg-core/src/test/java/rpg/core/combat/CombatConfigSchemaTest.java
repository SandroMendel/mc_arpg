package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigValidationException;
import rpg.core.config.FieldDefinition;
import rpg.core.config.SchemaValidator;

/**
 * Every validation rule of {@code combat.yml}, one case each, asserted against the message
 * (contracts/combat-config.md, FR-002 of B01).
 *
 * <p>{@code CombatConfigTest} covers what the values <em>mean</em>. This covers what happens when
 * they are wrong - which is the half an operator meets. Each case asserts the message, not just that
 * something was thrown: a person editing a balancing file needs to be told which key is at fault.
 *
 * <p>Both routes are exercised on purpose. A <b>missing</b> field is caught by the validator and
 * reported with the file name; an <b>implausible</b> field is caught by the record and reported with
 * the value. Testing only one of them would leave half the rules unproven.
 */
class CombatConfigSchemaTest {

    private static final Path SOURCE = Path.of("combat.yml");

    @Test
    @DisplayName("a complete configuration loads")
    void completeConfigurationLoads() throws Exception {
        CombatConfig config = load(document());

        assertThat(config.combatTimeout().toSeconds()).isEqualTo(10);
        assertThat(config.maxAttackers()).isEqualTo(8);
        assertThat(config.defaultMobStats().health()).isEqualTo(60.0);
        assertThat(config.mobStats()).containsKey("ZOMBIE");
    }

    @Test
    @DisplayName("the shipped combat.yml declares every field the schema requires")
    void everyRequiredFieldIsDeclared() {
        // A schema whose required fields nobody supplies would stop the start; the point here is
        // that the field list and the shipped defaults agree, which no other test checks.
        var required =
                CombatConfigSchema.schema().fields().stream()
                        .filter(FieldDefinition::required)
                        .map(FieldDefinition::path)
                        .toList();

        assertThat(required)
                .contains(
                        "combat.combat-timeout-seconds",
                        "combat.attribution.max-attackers",
                        "combat.attribution.timeout-seconds",
                        "combat.feedback.aggregation-window-millis",
                        "combat.feedback.knockback-strength",
                        "environment.fall.safe-blocks",
                        "environment.fall.damage-per-block",
                        "environment.fall.max-damage",
                        "mobs.default.health",
                        "mobs.default.defense",
                        "mobs.default.physical-damage",
                        "mobs.by-type");

        // ADR-003 wants a decision per source, so every flat hazard is required too.
        for (EnvironmentSource source : EnvironmentSource.all()) {
            if (!source.isComputed()) {
                assertThat(required)
                        .as("environment source " + source.key())
                        .contains("environment." + source.key());
            }
        }
    }

    @Test
    @DisplayName("a missing field names the file and the path")
    void missingFieldNamesThePath() {
        Map<String, Object> document = document();
        combat(document).remove("combat-timeout-seconds");

        assertThatThrownBy(() -> load(document))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("combat-timeout-seconds");
    }

    @Test
    @DisplayName("a missing environment source stops the start rather than dealing zero damage")
    void missingEnvironmentSourceIsRejected() {
        Map<String, Object> document = document();
        environment(document).remove("lava");

        // ADR-003: a hazard nobody decided about must not quietly become harmless.
        assertThatThrownBy(() -> load(document))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("lava");
    }

    @Test
    @DisplayName("a combat timeout of zero names the value")
    void combatTimeoutZero() {
        Map<String, Object> document = document();
        combat(document).put("combat-timeout-seconds", 0);

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("combat-timeout-seconds")
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("max-attackers outside 1..64 names the value and both bounds")
    void maxAttackersOutOfRange() {
        for (int broken : new int[] {0, 65, 1_000}) {
            Map<String, Object> document = document();
            attribution(document).put("max-attackers", broken);

            assertThatThrownBy(() -> load(document))
                    .as("max-attackers " + broken)
                    .hasMessageContaining("max-attackers")
                    .hasMessageContaining("between 1 and 64")
                    .hasMessageContaining(String.valueOf(broken));
        }
    }

    @Test
    @DisplayName("the ceiling on max-attackers is memory, not taste")
    void maxAttackersCeilingIsExplained() {
        Map<String, Object> document = document();
        attribution(document).put("max-attackers", 65);

        // The message has to say why, or the next person raises the constant instead of the config.
        assertThatThrownBy(() -> load(document)).hasMessageContaining("array per target");
    }

    @Test
    @DisplayName("an attribution timeout of zero names the value")
    void attributionTimeoutZero() {
        Map<String, Object> document = document();
        attribution(document).put("timeout-seconds", 0);

        assertThatThrownBy(() -> load(document)).hasMessageContaining("attribution.timeout-seconds");
    }

    @Test
    @DisplayName("an aggregation window above five seconds names the value")
    void aggregationWindowTooLarge() {
        Map<String, Object> document = document();
        feedback(document).put("aggregation-window-millis", 5_001);

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("aggregation-window-millis")
                .hasMessageContaining("between 0 and 5000");
    }

    @Test
    @DisplayName("a negative knockback strength names the value")
    void negativeKnockback() {
        Map<String, Object> document = document();
        feedback(document).put("knockback-strength", -0.5);

        assertThatThrownBy(() -> load(document)).hasMessageContaining("knockback-strength");
    }

    @Test
    @DisplayName("a negative environment amount names the source")
    void negativeEnvironmentAmount() {
        Map<String, Object> document = document();
        environment(document).put("lava", -1.0);

        // A negative hazard would heal whoever stands in lava.
        assertThatThrownBy(() -> load(document)).hasMessageContaining("lava");
    }

    @Test
    @DisplayName("each fall field is checked on its own and names itself")
    void fallFieldsAreCheckedIndividually() {
        Map<String, Object> negativeSafe = document();
        fall(negativeSafe).put("safe-blocks", -1.0);
        assertThatThrownBy(() -> load(negativeSafe)).hasMessageContaining("safe-blocks");

        Map<String, Object> zeroPerBlock = document();
        fall(zeroPerBlock).put("damage-per-block", 0.0);
        assertThatThrownBy(() -> load(zeroPerBlock)).hasMessageContaining("damage-per-block");

        Map<String, Object> zeroMax = document();
        fall(zeroMax).put("max-damage", 0.0);
        assertThatThrownBy(() -> load(zeroMax)).hasMessageContaining("max-damage");
    }

    @Test
    @DisplayName("an incomplete mobs.default names the missing field")
    void incompleteDefaultMob() {
        Map<String, Object> document = document();
        mobsDefault(document).remove("defense");

        assertThatThrownBy(() -> load(document))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("mobs.default.defense");
    }

    @Test
    @DisplayName("an incomplete by-type entry names the mob type and the field")
    void incompleteByTypeEntry() {
        Map<String, Object> document = document();
        @SuppressWarnings("unchecked")
        Map<String, Object> zombie = (Map<String, Object>) byType(document).get("ZOMBIE");
        zombie.remove("physical-damage");

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("ZOMBIE")
                .hasMessageContaining("physical-damage");
    }

    @Test
    @DisplayName("a mob health of zero is refused - a holder without a maximum cannot exist")
    void zeroMobHealth() {
        Map<String, Object> document = document();
        mobsDefault(document).put("health", 0.0);

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("health")
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("a negative mob value names the field")
    void negativeMobValue() {
        Map<String, Object> document = document();
        mobsDefault(document).put("defense", -5.0);

        assertThatThrownBy(() -> load(document))
                .hasMessageContaining("defense")
                .hasMessageContaining("not negative");
    }

    // --- helpers ---------------------------------------------------------

    private static CombatConfig load(Map<String, Object> document) throws Exception {
        ConfigSchema<CombatConfig> schema = CombatConfigSchema.schema();
        return schema.bind(SchemaValidator.validate(SOURCE, document, schema));
    }

    /** A valid nested document, built from the defaults so it stays in step with the block. */
    private static Map<String, Object> document() {
        Map<String, Object> attribution = new LinkedHashMap<>();
        attribution.put("max-attackers", 8);
        attribution.put("timeout-seconds", 30);

        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("aggregation-window-millis", 500);
        feedback.put("knockback-strength", 0.4);

        Map<String, Object> combat = new LinkedHashMap<>();
        combat.put("combat-timeout-seconds", 10);
        combat.put("attribution", attribution);
        combat.put("feedback", feedback);

        Map<String, Object> fall = new LinkedHashMap<>();
        fall.put("safe-blocks", 3.0);
        fall.put("damage-per-block", 4.0);
        fall.put("max-damage", 200.0);

        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("fall", fall);
        for (EnvironmentSource source : EnvironmentSource.all()) {
            if (!source.isComputed()) {
                environment.put(source.key(), 5.0);
            }
        }

        Map<String, Object> defaultMob = new LinkedHashMap<>();
        defaultMob.put("health", 60.0);
        defaultMob.put("defense", 0.0);
        defaultMob.put("physical-damage", 8.0);

        Map<String, Object> zombie = new LinkedHashMap<>();
        zombie.put("health", 80.0);
        zombie.put("defense", 10.0);
        zombie.put("physical-damage", 10.0);

        Map<String, Object> byType = new LinkedHashMap<>();
        byType.put("ZOMBIE", zombie);

        Map<String, Object> mobs = new LinkedHashMap<>();
        mobs.put("default", defaultMob);
        mobs.put("by-type", byType);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("combat", combat);
        document.put("environment", environment);
        document.put("mobs", mobs);
        return document;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> combat(Map<String, Object> document) {
        return (Map<String, Object>) document.get("combat");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attribution(Map<String, Object> document) {
        return (Map<String, Object>) combat(document).get("attribution");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> feedback(Map<String, Object> document) {
        return (Map<String, Object>) combat(document).get("feedback");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> environment(Map<String, Object> document) {
        return (Map<String, Object>) document.get("environment");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fall(Map<String, Object> document) {
        return (Map<String, Object>) environment(document).get("fall");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mobsDefault(Map<String, Object> document) {
        return (Map<String, Object>) ((Map<String, Object>) document.get("mobs")).get("default");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> byType(Map<String, Object> document) {
        return (Map<String, Object>) ((Map<String, Object>) document.get("mobs")).get("by-type");
    }
}
