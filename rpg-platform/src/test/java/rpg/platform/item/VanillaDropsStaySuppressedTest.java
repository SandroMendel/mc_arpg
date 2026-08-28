package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-022 — <b>Vanillas eigene Drops sind unterdrückt, und zwar schon.</b>
 *
 * <p>B05s {@code CombatDeathListener} räumt {@code getDrops()} und setzt {@code setDroppedExp(0)}.
 * Die Anforderung ist damit ohne eine Zeile Code dieses Blocks erfüllt (research.md R3) — was eine
 * angenehme Überraschung war und genau deshalb einen Test verdient.
 *
 * <p><b>Warum eine Quelltextprüfung und kein Verhaltenstest.</b> Der Verhaltenstest gehört B05 und
 * existiert dort. Was <em>hier</em> schiefgehen kann, ist etwas anderes: jemand fasst B05 an, nimmt
 * die zwei Zeilen heraus, und B05s eigene Tests bleiben grün, weil sie über Erfahrung und Schaden
 * gehen. Auffallen würde es auf dem Server — als Kreatur, die plötzlich rohes Rindfleisch fallen
 * lässt, und niemand käme auf B05.
 *
 * <p>Diese Prüfung liest deshalb nach, dass die beiden Zeilen noch da sind, und nennt in ihrer
 * Meldung den Block, der sie halten muss.
 */
class VanillaDropsStaySuppressedTest {

    private static final Path B05_LISTENER =
            Path.of("src", "main", "java", "rpg", "platform", "combat", "CombatDeathListener.java");

    @Test
    @DisplayName("B05 raeumt die Vanilla-Beute - und B11 baut deshalb nichts nach")
    void b05StillClearsTheVanillaDrops() throws IOException {
        String code = Files.readString(B05_LISTENER);

        assertThat(code)
                .as(
                        "FR-022 haengt an dieser Zeile in B05. Faellt sie weg, lassen Kreaturen"
                                + " wieder Vanilla-Beute fallen - und man suchte den Fehler in B11")
                .contains("getDrops().clear()");
    }

    @Test
    @DisplayName("und die Vanilla-Erfahrung ebenso - B06 liefert seine eigene")
    void b05StillClearsTheVanillaExperience() throws IOException {
        String code = Files.readString(B05_LISTENER);

        assertThat(code)
                .as("sonst gaebe es Erfahrung zweimal: einmal von Vanilla, einmal von B06")
                .contains("setDroppedExp(0)");
    }

    @Test
    @DisplayName("B11 unterdrueckt selbst NICHTS - eine zweite Unterdrueckung waere eine zweite Wahrheit")
    void b11SuppressesNothingItself() throws IOException {
        // Der eigentliche Punkt dieses Tests. Die Versuchung ist, "sicherheitshalber" noch einmal
        // zu raeumen - und dann gibt es zwei Stellen, die dasselbe tun, von denen eine beim
        // naechsten Umbau vergessen wird.
        Path itemPackage = Path.of("src", "main", "java", "rpg", "platform", "item");
        try (var sources = Files.walk(itemPackage)) {
            var offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(
                                    path -> {
                                        try {
                                            // Kommentare raus, bevor gesucht wird - nach dem
                                            // Muster von ConfigOnlyMobTest.codeOnly. Der erste
                                            // Anlauf schlug an einem Javadoc an, das erklaert,
                                            // WARUM hier nichts steht, und machte damit eine
                                            // richtige Erklaerung zum Verstoss.
                                            String code = codeOnly(Files.readString(path));
                                            return code.contains("getDrops()")
                                                    || code.contains("setDroppedExp");
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as("FR-022 ist bereits erfuellt - hier ist nichts zu tun (research.md R3)")
                    .isEmpty();
        }
    }

    /** Kommentare weg — ein Javadoc, das etwas erklärt, ist kein Aufruf. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
