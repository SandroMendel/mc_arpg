package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T039 - der Tod kostet keine Coins (FR-012, US1 Szenario 8).
 *
 * <p>Bei `/clarify` entschieden, und die Entscheidung zieht die Linie von ADR-017 gerade durch: der
 * Tod kostet Haltbarkeit und Zeit, nicht Fortschritt. Kein Item- und kein XP-Verlust, also auch kein
 * Coin-Verlust.
 *
 * <p><b>Der zweite Test ist der eigentliche.</b> Dass ein Tod heute nichts abbucht, folgt schon
 * daraus, dass niemand die Buchung aufruft - das ist leicht und unabsichtlich zu aendern. Dass es
 * <em>keinen Buchungsgrund dafuer gibt</em>, ist die Zusage, die haelt: wer den Verlust nachtraegt,
 * muss den Vorrat erweitern und stolpert dabei ueber diesen Test.
 */
class DeathDoesNotCostCoinsTest {

    private final UUID character = UUID.randomUUID();

    @Test
    @DisplayName("ein Stand ueberlebt einen Tod unveraendert")
    void deathLeavesTheBalanceAlone() {
        CurrencyFixture.Harness harness = CurrencyFixture.loadedWith(character, 750L);

        // Ein Tod ist fuer diesen Block kein Ereignis: es gibt keinen Einhaengepunkt, der ihn
        // hoerte, und keine Methode, die er riefe. Der Stand bleibt schlicht stehen.
        assertThat(harness.currency.balanceOf(character)).hasValue(750L);
        assertThat(harness.ledger.entries).isEmpty();
    }

    @Test
    @DisplayName("es gibt keinen Buchungsgrund fuer einen Todesverlust - und das ist die Zusage")
    void thereIsNoReasonForADeathPenalty() {
        List<String> reasons =
                Arrays.stream(BookingReason.values()).map(Enum::name).toList();

        assertThat(reasons)
                .as("wer den Verlust nachtraegt, muss hier vorbei")
                .doesNotContain("DEATH", "DEATH_PENALTY", "ON_DEATH", "DEATH_LOSS");
        assertThat(reasons)
                .allSatisfy(name -> assertThat(name).doesNotContain("DEATH"));
    }

    @Test
    @DisplayName("keine Quelle des Blocks nennt einen Todesverlust")
    void noSourceInTheBlockMentionsADeathPenalty() throws Exception {
        Path currencyPackage =
                repositoryRoot().resolve("rpg-core/src/main/java/rpg/core/currency");

        try (Stream<Path> sources = Files.walk(currencyPackage)) {
            List<Path> offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(DeathDoesNotCostCoinsTest::mentionsADeathPenalty)
                            .toList();

            assertThat(offenders)
                    .as("der Block hoert den Tod nicht, und das soll sichtbar bleiben")
                    .isEmpty();
        }
    }

    private static boolean mentionsADeathPenalty(Path source) {
        try {
            String text = Files.readString(source);
            // Der Kommentar in BookingReason erklaert, warum es den Grund NICHT gibt - das ist
            // erwuenscht. Gesucht wird ausfuehrbarer Bezug, nicht die Begruendung.
            return text.contains("onDeath") || text.contains("DEATH_PENALTY");
        } catch (Exception unreadable) {
            throw new IllegalStateException("could not read " + source, unreadable);
        }
    }

    /** Findet die Gradle-Wurzel ueber {@code settings.gradle.kts}, wie die uebrigen Quelltests. */
    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("settings.gradle.kts not found above the working dir");
        }
        return candidate;
    }
}
