package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.StatisticsMessageKeys;

/**
 * FR-007c — <b>keine Ansicht dieses Blocks gibt eine Kill-Zahl als eigenhändig erledigte
 * Kreaturen aus.</b>
 *
 * <h2>Warum eine Beschriftung einen Test verdient</h2>
 *
 * <p>ADR-042 hat entschieden, dass ein Kill <em>jedem</em> Beteiligten oberhalb der Schwelle
 * zählt. Das ist die richtige Regel — und sie hat einen Preis, der sich nicht wegdefinieren
 * lässt: <b>die Summe aller Zähler übersteigt die Zahl tatsächlich gestorbener Kreaturen.</b> Bei
 * einer Party von fünf, die gemeinsam hundert Kreaturen erlegt, stehen bis zu fünfhundert in den
 * Zählern.
 *
 * <p>Der Preis ist angenommen. Aber er muss auch dastehen. Stünde über der Liste „Creatures
 * Killed", wäre die Zahl schlicht falsch — und der erste Spieler, der zwei Zahlen vergleicht,
 * hätte recht damit, sie zu melden. Steht dort „Kill Participation", ist dieselbe Zahl richtig.
 *
 * <p>Deshalb prüft dieser Test die <b>ausgelieferten Texte</b> und nicht den Code: der Fehler
 * entstünde beim Umformulieren einer Zeile in {@code messages.yml}, wo kein Compiler und kein
 * Verhaltenstest hinsieht.
 */
class KillBoardIsLabelledParticipationTest {

    /** Wörter, die alleinige Urheberschaft behaupten. */
    private static final List<String> CLAIMS_SOLE_AUTHORSHIP =
            List.of("creatures killed", "you killed", "mobs slain", "kills made", "erledigte");

    @Test
    @DisplayName("FR-007c - die Kill-Ranglisten heissen Beteiligung, nicht erledigte Kreaturen")
    void thekillBoardsAreLabelledParticipation() throws Exception {
        Messages messages = loadMessages();

        for (Aggregation board : List.of(Aggregation.MOB_KILLS, Aggregation.BOSS_KILLS)) {
            String name = messages.get(StatisticsMessageKeys.boardName(board));

            assertThat(name.toLowerCase(Locale.ROOT))
                    .as("%s benennt die Beteiligung", board.key())
                    .contains("participation");
        }
    }

    @Test
    @DisplayName("FR-007c - kein Text der Kill-Ranglisten behauptet alleinige Urheberschaft")
    void notextOfTheKillBoardsClaimsSoleAuthorship() throws Exception {
        Messages messages = loadMessages();

        for (Aggregation board : List.of(Aggregation.MOB_KILLS, Aggregation.BOSS_KILLS)) {
            String both =
                    (messages.get(StatisticsMessageKeys.boardName(board))
                                    + " "
                                    + messages.get(StatisticsMessageKeys.boardHint(board)))
                            .toLowerCase(Locale.ROOT);

            assertThat(CLAIMS_SOLE_AUTHORSHIP)
                    .as("keine dieser Wendungen darf in %s stehen", board.key())
                    .noneMatch(both::contains);
        }
    }

    @Test
    @DisplayName("die erklaerende Zeile ist da, und sie erklaert wirklich etwas")
    void theexplainingLineIsThereAndExplainsSomething() throws Exception {
        Messages messages = loadMessages();

        String hint = messages.get(StatisticsMessageKeys.boardHint(Aggregation.MOB_KILLS));

        assertThat(hint)
                .as("eine Zeile, die den Unterschied zwischen Beteiligung und letztem Schlag nennt")
                .isNotBlank();
        assertThat(hint.toLowerCase(Locale.ROOT)).contains("last hit");
    }

    @Test
    @DisplayName("jede Rangliste hat eine erklaerende Zeile, nicht nur die beiden")
    void everyBoardHasAnExplainingLine() throws Exception {
        Messages messages = loadMessages();

        // Eine Zeile, die nur manchmal da ist, liest sich wie eine Entschuldigung.
        for (Aggregation board : Aggregation.values()) {
            assertThat(messages.contains(StatisticsMessageKeys.boardHint(board)))
                    .as("%s hat eine erklaerende Zeile", board.key())
                    .isTrue();
        }
    }

    private static Messages loadMessages() throws Exception {
        Map<String, String> flat = new LinkedHashMap<>();
        flatten("", loadYaml(), flat);
        return new MapMessages(flat);
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> body, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                flatten(path, (Map<String, Object>) nested, out);
            } else if (entry.getValue() != null) {
                out.put(path, String.valueOf(entry.getValue()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml() throws Exception {
        try (InputStream stream =
                KillBoardIsLabelledParticipationTest.class
                        .getClassLoader()
                        .getResourceAsStream("messages.yml")) {
            assertThat(stream).as("messages.yml liegt auf dem Klassenpfad").isNotNull();
            return (Map<String, Object>)
                    new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
