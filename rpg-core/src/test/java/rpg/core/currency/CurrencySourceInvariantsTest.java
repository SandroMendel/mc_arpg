package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T114, T115 - Zusagen ueber den Quelltext, die kein Verhaltenstest sehen kann.
 *
 * <p>Ein Quelltest ist die einzige Form, in der sich „hier steht etwas <b>nicht</b>" pruefen laesst.
 * Er wird rot, wenn jemand die Regel bricht, und nicht erst, wenn ein Spieler es merkt.
 */
class CurrencySourceInvariantsTest {

    private static final Path CORE_CURRENCY =
            repositoryRoot().resolve("rpg-core/src/main/java/rpg/core/currency");

    @Test
    @DisplayName("kein Bukkit in der Regelschicht (Prinzip III)")
    void noBukkitInTheRuleLayer() throws IOException {
        List<String> forbidden =
                List.of("org.bukkit", "io.papermc", "net.minecraft", "org.spigotmc");

        List<String> offenders = new ArrayList<>();
        for (Path source : sources(CORE_CURRENCY)) {
            String text = Files.readString(source);
            for (String needle : forbidden) {
                if (text.contains(needle)) {
                    offenders.add(source.getFileName() + " nennt " + needle);
                }
            }
        }

        assertThat(offenders)
                .as("nur eine bukkitfreie Regelschicht ist ohne laufenden Server pruefbar")
                .isEmpty();
    }

    @Test
    @DisplayName("es gibt keinen Weg, einen Stand ohne Grund zu aendern (FR-005, SC-004)")
    void noBalanceChangesWithoutAReason() throws IOException {
        // Jede Methode, die bucht, nimmt einen BookingReason. Waere das nur eine Verabredung, kaeme
        // frueher oder spaeter eine Abkuerzung dazu - und dann liesse sich eine Fehlbuchung nicht
        // mehr zuordnen, was der einzige Zweck des Verlaufs ist.
        String currency = Files.readString(CORE_CURRENCY.resolve("Currency.java"));

        assertThat(currency).contains("BookingResult credit(UUID characterId, long amount, BookingReason reason)");
        assertThat(currency).contains("BookingResult debit(UUID characterId, long amount, BookingReason reason)");
        assertThat(currency)
                .as("keine zweite, grundlose Fassung")
                .doesNotContain("credit(UUID characterId, long amount)")
                .doesNotContain("debit(UUID characterId, long amount)");
    }

    @Test
    @DisplayName("es gibt keine unbegrenzte Verlaufsabfrage (FR-046a)")
    void thereIsNoUnboundedLedgerRead() throws IOException {
        String ledger = Files.readString(CORE_CURRENCY.resolve("CoinLedger.java"));

        assertThat(ledger)
                .as("die groesste Tabelle des Projekts vertraegt keine Methode, die alles liefert")
                .doesNotContain("historyOf(UUID characterId)")
                .doesNotContain("allEntriesOf");
        assertThat(ledger).contains("int offset, int limit");
    }

    @Test
    @DisplayName("dieser Block kennt keinen Preiskatalog (ADR-027, FR-053)")
    void thereIsNoPriceCatalogue() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : sources(CORE_CURRENCY)) {
            String text = Files.readString(source);
            if (text.contains("PriceCatalog")
                    || text.contains("priceOf(")
                    || text.contains("PriceList")) {
                offenders.add(source.getFileName().toString());
            }
        }

        assertThat(offenders)
                .as("Preise stehen bei dem, der sie verlangt - ein zweiter Ort waere ein zweiter"
                        + " Wahrheitswert fuer dieselbe Zahl")
                .isEmpty();
    }

    @Test
    @DisplayName("der Block plant keine Aufgabe (Prinzip II)")
    void nothingIsScheduled() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : sources(CORE_CURRENCY)) {
            String text = Files.readString(source);
            if (text.contains("Scheduler") || text.contains("runAsyncDelayed")) {
                offenders.add(source.getFileName().toString());
            }
        }

        assertThat(offenders)
                .as("der Verfall gehoert Vanilla, der Verlauf reitet auf dem Flush - nichts eigenes")
                .isEmpty();
    }

    private static List<Path> sources(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
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
