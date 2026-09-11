package rpg.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * <b>Keine Beschreibung darf als roher Schlüssel vor Augen kommen.</b>
 *
 * <p>Gefunden am 2026-09-06 auf dem echten Server, nicht von einem Test: {@code help trash} zeigte
 * {@code Description: command.trash.description}. Der Baum reichte den {@link MessageKey} an
 * Brigadier durch, weil im Code stand, das sei „Betreibertext im Log" — es steht aber in der
 * Serverhilfe, und die liest ein Mensch.
 *
 * <p>Derselbe Fehler wie ein Zonenname, der im Spiel als {@code zone.greenfields.name} auftaucht.
 * B09 bricht dafür den Start ab; hier sorgt dieser Test dafür, dass es nicht wiederkommt.
 *
 * <p><b>Der Test sucht die Schlüssel selbst</b>, statt eine Liste zu führen: jedes
 * {@code command.<name>.description} in der ausgelieferten Datei muss einen Text haben, und jedes
 * Kommando, das eine Beschreibung deklariert, muss einen Schlüssel benutzen, den es gibt. Eine von
 * Hand gepflegte Liste hier wäre beim übernächsten Kommando unvollständig.
 */
class CommandDescriptionsResolveTest {

    @Test
    @DisplayName("jede command.*.description in messages.yml loest zu echtem Text auf")
    void everyDescriptionResolves() throws Exception {
        Messages messages = MapMessages.fromNested(shipped());

        List<String> keys = descriptionKeys(shipped());

        assertThat(keys)
                .as("ohne mindestens eine Beschreibung prueft dieser Test nichts")
                .isNotEmpty();

        for (String key : keys) {
            MessageKey messageKey = MessageKey.of(key);
            assertThat(messages.contains(messageKey)).as("%s fehlt", key).isTrue();
            assertThat(messages.get(messageKey))
                    .as("%s ist leer - eine Hilfe ohne Text sieht aus wie ein Kommando ohne Zweck", key)
                    .isNotBlank();
            assertThat(messages.get(messageKey))
                    .as("%s traegt sich selbst als Text - das ist der Fehler vom 2026-09-06", key)
                    .isNotEqualTo(key);
        }
    }

    @Test
    @DisplayName("die Beschreibung von /trash steht in messages.yml und nicht mehr in plugin.yml")
    void thetrashDescriptionMovedOut() throws Exception {
        Messages messages = MapMessages.fromNested(shipped());

        assertThat(messages.contains(MessageKey.of("command.trash.description"))).isTrue();

        String pluginYml = read("/plugin.yml");
        assertThat(pluginYml)
                .as("seit dem Umzug auf Brigadier liest niemand mehr den commands:-Eintrag")
                .doesNotContain("  trash:");
    }

    // --- Aufbau ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> shipped() throws Exception {
        return new Yaml().load(read("/messages.yml"));
    }

    private static String read(String resource) throws Exception {
        try (InputStream stream = CommandDescriptionsResolveTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(resource + " ist nicht im Klassenpfad");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Alle {@code command.<etwas>.description} aus der verschachtelten Form. */
    @SuppressWarnings("unchecked")
    private static List<String> descriptionKeys(Map<String, Object> nested) {
        Object command = nested.get("command");
        if (!(command instanceof Map<?, ?> branch)) {
            return List.of();
        }
        Map<String, String> found = new LinkedHashMap<>();
        ((Map<String, Object>) branch)
                .forEach(
                        (name, value) -> {
                            if (value instanceof Map<?, ?> leaf
                                    && ((Map<String, Object>) leaf).get("description") != null) {
                                found.put("command." + name + ".description", "");
                            }
                        });
        return List.copyOf(found.keySet());
    }
}
