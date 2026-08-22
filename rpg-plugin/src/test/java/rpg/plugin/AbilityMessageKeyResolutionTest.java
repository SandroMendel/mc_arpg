package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.ability.Ability;
import rpg.core.ability.AbilityConfig;
import rpg.core.ability.AbilityConfigSchema;
import rpg.core.ability.AbilityMessageKeys;
import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.platform.config.YamlConfigLoader;

/**
 * T134 - jeder Schlüssel, den B08 aussprechen kann, trägt in der ausgelieferten {@code messages.yml}
 * Text (Prinzip V).
 *
 * <p>Zwei Gruppen, und die zweite ist die grössere: die vierzehn festen Schlüssel des Blocks, und die
 * <b>achtzehn Anzeigenamen</b>, die aus {@code abilities.yml} kommen. Bei den zweiten fällt ein
 * Tippfehler sonst erst im Spiel auf, und zwar als Schlüsseltext auf einem Gegenstand in der Hotbar.
 *
 * <p>Geprüft wird nicht nur die Anwesenheit - die prüft der Startvalidator schon -, sondern dass der
 * Text nicht leer ist und dass jeder Platzhalter, den der Code füllt, im Text überhaupt vorkommt. Ein
 * fehlender Platzhalter ist eine Zahl, die niemand sieht, und nichts würde sich beschweren.
 */
class AbilityMessageKeyResolutionTest {

    private static final Path MESSAGES = Path.of("src/main/resources");

    @Test
    @DisplayName("jeder feste Schlüssel des Blocks löst zu nicht-leerem Text auf")
    void everyFixedKeyResolves() {
        Messages messages = shipped();
        assertThat(AbilityMessageKeys.all()).isNotEmpty();

        for (MessageKey key : AbilityMessageKeys.all()) {
            assertResolves(messages, key);
        }
    }

    @Test
    @DisplayName("alle achtzehn Anzeigenamen lösen auf - sonst stünde ein Schlüssel in der Hotbar")
    void everyDisplayNameResolves() throws Exception {
        Messages messages = shipped();
        List<String> unresolved = new ArrayList<>();

        for (Ability ability : shippedAbilities().abilities().values()) {
            String text = messages.get(ability.displayNameKey());
            if (text == null || text.isBlank() || text.equals(ability.displayNameKey().value())) {
                unresolved.add(ability.id() + " -> " + ability.displayNameKey().value());
            }
        }

        assertThat(unresolved)
                .as("ein unaufgelöster Schlüssel erscheint als Schlüssel auf dem Gegenstand")
                .isEmpty();
    }

    @Test
    @DisplayName("die Ablehnungen führen die Platzhalter, die der Code füllt")
    void therejectionsCarryTheirPlaceholders() {
        Messages messages = shipped();

        assertThat(messages.get(AbilityMessageKeys.ON_COOLDOWN)).contains("{seconds}");
        assertThat(messages.get(AbilityMessageKeys.NOT_ENOUGH_MANA)).contains("{cost}");
        assertThat(messages.get(AbilityMessageKeys.NOT_UNLOCKED)).contains("{level}");
        assertThat(messages.get(AbilityMessageKeys.ALREADY_SUSTAINING)).contains("{ability}");
    }

    @Test
    @DisplayName("Freischaltung, Rang und Einstellung nennen die Fähigkeit")
    void theProgressMessagesNameTheAbility() {
        Messages messages = shipped();

        assertThat(messages.get(AbilityMessageKeys.UNLOCKED)).contains("{ability}");
        assertThat(messages.get(AbilityMessageKeys.RANK_ADVANCED))
                .contains("{ability}")
                .contains("{rank}");
        assertThat(messages.get(AbilityMessageKeys.RANK_AT_MAXIMUM)).contains("{ability}");
        assertThat(messages.get(AbilityMessageKeys.TOGGLE_CHANGED))
                .contains("{ability}")
                .contains("{state}");
    }

    // --- helpers ---

    private static void assertResolves(Messages messages, MessageKey key) {
        assertThat(messages.get(key))
                .as("Schlüssel %s", key.value())
                .isNotNull()
                .isNotBlank()
                .as("ein Schlüssel, der sich selbst zurückgibt, ist ein fehlender Eintrag")
                .isNotEqualTo(key.value());
    }

    private static AbilityConfig shippedAbilities() throws Exception {
        ConfigSchema<AbilityConfig> schema = AbilityConfigSchema.schema();
        try (InputStream stream =
                AbilityMessageKeyResolutionTest.class.getResourceAsStream("/abilities.yml")) {
            if (stream == null) {
                throw new IllegalStateException("abilities.yml is not on the classpath");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> document =
                    new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            return schema.bind(SchemaValidator.validate(Path.of("abilities.yml"), document, schema));
        }
    }

    /** Die ausgelieferte Datei, nicht eine Testfassung - sonst prüft der Test seine eigene Kopie. */
    private static Messages shipped() {
        try {
            return MapMessages.fromNested(
                    new YamlConfigLoader(MESSAGES).readDocument(Path.of("messages.yml")));
        } catch (Exception unreadable) {
            throw new AssertionError("could not read the shipped messages.yml", unreadable);
        }
    }
}
