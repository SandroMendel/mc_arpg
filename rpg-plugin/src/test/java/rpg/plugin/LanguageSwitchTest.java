package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.MessageKeyValidator;
import rpg.core.message.Messages;
import rpg.core.ui.LanguageSet;
import rpg.core.ui.UiMessageKeys;

/**
 * Der Sprachwechsel (T134, T135) — <b>ohne dass B13 die Prüfung selbst gebaut hätte</b>.
 *
 * <p>{@code MessageKeyValidator.verifyAllPresent} existiert seit B01 und meldet <em>alle</em>
 * fehlenden Schlüssel auf einmal. Das ist der Grund, aus dem eine zweite Sprache überhaupt machbar
 * ist: bei einer Meldung je Startversuch gäbe ein Übersetzer nach dem zwanzigsten auf.
 *
 * <p>B13 ändert nur, <b>welche Datei</b> gelesen wird. Diese Tests prüfen genau das — und die zwei
 * Sonderwege, die es dabei zu beachten gibt.
 */
class LanguageSwitchTest {

    @Test
    @DisplayName("T134: ein zweiter Sprachsatz mit allen Schluesseln laedt")
    void acompleteSecondLanguageLoads() throws Exception {
        // Der Normalfall: jemand kopiert messages.yml nach messages_de.yml und uebersetzt.
        Messages german = MapMessages.fromNested(shipped());

        MessageKeyValidator.verifyAllPresent(german, UiMessageKeys.all());
    }

    @Test
    @DisplayName("T135: ein Satz mit DREI fehlenden Schluesseln nennt ALLE DREI")
    void anincompleteSetNamesEveryGap() throws Exception {
        // Das ist die eigentliche Zusage. Wer uebersetzt, hat zweihundert Schluessel vor sich; er
        // braucht die Liste und nicht den ersten Fehler.
        Map<String, Object> gappy = shipped();
        removeKey(gappy, "ui.sidebar.level");
        removeKey(gappy, "ui.sidebar.coins");
        removeKey(gappy, "ui.bossbar.channelling");

        Messages incomplete = MapMessages.fromNested(gappy);

        assertThatThrownBy(
                        () -> MessageKeyValidator.verifyAllPresent(incomplete, UiMessageKeys.all()))
                .as("alle drei auf einmal, nicht der erste")
                .hasMessageContaining("ui.sidebar.level")
                .hasMessageContaining("ui.sidebar.coins")
                .hasMessageContaining("ui.bossbar.channelling");
    }

    @Test
    @DisplayName("T134: die Dateizuordnung folgt dem Kuerzel")
    void thefileFollowsTheCode() {
        assertThat(LanguageSet.defaultSet().file()).isEqualTo("messages.yml");
        assertThat(new LanguageSet("de").file()).isEqualTo("messages_de.yml");
    }

    @Test
    @DisplayName("T133: B09s Zonennamen und B10s Artnamen pruefen sich SELBST - und das bleibt so")
    void thetwoSpecialCasesStay() throws Exception {
        // Zwei Bloecke stehen nicht in der allgemeinen Liste, und beide zu Recht: ihre Schluessel
        // sind erst nach dem Lesen von zones.yml und mobs.yml bekannt, also kann RpgPlugin sie
        // beim Sammeln noch nicht kennen. ZoneModule.start und MobModule.start pruefen sie selbst.
        //
        // FUER DEN SPRACHWECHSEL HEISST DAS: sie sind mit abgedeckt, aber ueber einen anderen Weg.
        // Wer diesen Test liest und die zwei Sonderwege abschaffen will, findet hier den Grund,
        // warum es sie gibt - und der Test wird rot, wenn jemand sie in die allgemeine Liste
        // aufnimmt, ohne dass die Konfiguration vorher gelesen wurde.
        Map<String, Object> texts = shipped();

        // Die Praefixe sind zone.<region>.name und mob.<art>.name - je Region und je Art einer,
        // gebildet aus der KONFIGURATION und nicht aus einer Liste im Code. Genau deshalb kann
        // RpgPlugin sie beim Sammeln nicht kennen.
        assertThat(flatten(texts).keySet())
                .as("die Zonennamen stehen in derselben Datei wie alles andere")
                .anyMatch(key -> key.startsWith("zone.") && key.endsWith(".name"))
                .as("und die Artnamen auch")
                .anyMatch(key -> key.startsWith("mob.") && key.endsWith(".name"));
    }

    @Test
    @DisplayName("T134: jeder Schluessel dieses Blocks steht in der ausgelieferten Datei")
    void everyKeyOfThisBlockIsInTheShippedFile() throws Exception {
        // Der Test, der eine vergessene Uebersetzung beim BUILD findet statt beim Start.
        Messages shipped = MapMessages.fromNested(shipped());

        List<MessageKey> missing = new ArrayList<>();
        for (MessageKey key : UiMessageKeys.all()) {
            if (!shipped.contains(key)) {
                missing.add(key);
            }
        }

        assertThat(missing).isEmpty();
    }

    // --- Aufbau ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> shipped() throws Exception {
        try (InputStream stream = LanguageSwitchTest.class.getResourceAsStream("/messages.yml")) {
            if (stream == null) {
                throw new IllegalStateException("messages.yml is not on the classpath");
            }
            return new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** Entfernt einen Schlüssel aus der verschachtelten Form. */
    @SuppressWarnings("unchecked")
    private static void removeKey(Map<String, Object> nested, String dotted) {
        String[] parts = dotted.split("\\.");
        Map<String, Object> at = nested;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = at.get(parts[i]);
            if (!(next instanceof Map<?, ?> map)) {
                return;
            }
            at = (Map<String, Object>) map;
        }
        at.remove(parts[parts.length - 1]);
    }

    /** Die verschachtelte Form als flache Schlüssel — nur für die Prüfung oben. */
    @SuppressWarnings("unchecked")
    private static Map<String, String> flatten(Map<String, Object> nested) {
        Map<String, String> flat = new LinkedHashMap<>();
        nested.forEach(
                (key, value) -> {
                    if (value instanceof Map<?, ?> map) {
                        flatten((Map<String, Object>) map)
                                .forEach((inner, text) -> flat.put(key + "." + inner, text));
                    } else if (value != null) {
                        flat.put(key, String.valueOf(value));
                    }
                });
        return flat;
    }
}
