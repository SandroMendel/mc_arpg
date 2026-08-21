package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.ClassMessageKeys;
import rpg.core.combat.CombatMessageKeys;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.platform.config.YamlConfigLoader;

/**
 * T128: jeder Schlüssel, den B07 und die Kampfanzeigen aussprechen können, steht in der ausgelieferten
 * {@code messages.yml} - und trägt Text, nicht nur einen Eintrag.
 *
 * <p>Der Startvalidator prüft die Anwesenheit schon und bricht sonst ab, also fängt dieser Test keinen
 * fehlenden Schlüssel zuerst. Er prüft zwei Dinge, die jener nicht prüft: dass der Text nicht leer ist,
 * und dass jeder Platzhalter, den der Code füllt, im ausgelieferten Text überhaupt vorkommt. Ein
 * Platzhalter, der im Text fehlt, ist eine Zahl, die niemand sieht - und nichts würde sich beschweren.
 */
class ClassMessageKeyResolutionTest {

    private static final Path MESSAGES = Path.of("src/main/resources");

    @Test
    @DisplayName("jeder Klassenschlüssel löst zu nicht-leerem Text auf")
    void everyClassKeyResolves() {
        assertEveryKeyResolves(ClassMessageKeys.all());
    }

    @Test
    @DisplayName("jeder Kampfschlüssel löst zu nicht-leerem Text auf")
    void everyCombatKeyResolves() {
        assertEveryKeyResolves(CombatMessageKeys.all());
    }

    @Test
    @DisplayName("die Lore-Zeilen führen die Platzhalter, die der Code füllt")
    void theSlotLoreCarriesItsPlaceholders() {
        Messages messages = shipped();

        assertThat(messages.get(ClassMessageKeys.SLOT_LEVEL)).contains("{level}");
        assertThat(messages.get(ClassMessageKeys.SLOT_TIERS))
                .contains("{armor}")
                .contains("{armor_max}")
                .contains("{weapon}")
                .contains("{weapon_max}");
        assertThat(messages.get(ClassMessageKeys.SLOT_LAST_PLAYED)).contains("{when}");
    }

    @Test
    @DisplayName("die Auswahlfrist nennt die verbleibenden Sekunden")
    void thetimeoutWarningCarriesItsPlaceholder() {
        assertThat(shipped().get(ClassMessageKeys.SELECTION_TIMEOUT_WARNING)).contains("{seconds}");
    }

    @Test
    @DisplayName("die Statuszeilen führen alle Zahlen, die sie zeigen sollen")
    void thereadoutsCarryTheirPlaceholders() {
        Messages messages = shipped();

        assertThat(messages.get(CombatMessageKeys.STATUS_ACTION_BAR))
                .contains("{health}")
                .contains("{max}")
                .contains("{percent}")
                .contains("{defense}");
        assertThat(messages.get(CombatMessageKeys.TARGET_REPORT))
                .contains("{target}")
                .contains("{health}")
                .contains("{max}")
                .contains("{percent}")
                .contains("{defense}")
                .contains("{damage}");
        assertThat(messages.get(CombatMessageKeys.TARGET_SLAIN))
                .contains("{target}")
                .contains("{damage}");
    }

    // --- fixtures ---

    private static void assertEveryKeyResolves(List<MessageKey> keys) {
        Messages messages = shipped();
        assertThat(keys).isNotEmpty();
        for (MessageKey key : keys) {
            assertThat(messages.get(key))
                    .as("Schlüssel %s", key.value())
                    .isNotNull()
                    .isNotBlank()
                    .as("ein Schlüssel, der sich selbst zurückgibt, ist ein fehlender Eintrag")
                    .isNotEqualTo(key.value());
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
