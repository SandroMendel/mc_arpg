package rpg.platform.currency;

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
 * T119 - ein Coin-Haufen wird nirgends gespeichert, und das ist Absicht (FR-033).
 *
 * <p>Ein Haufen ist eine <b>Gelegenheit mit Frist</b>, kein Besitz. Ihn zu persistieren hiesse, einen
 * dritten Aggregattyp fuer etwas anzulegen, das binnen Minuten ohnehin verfaellt - und beim naechsten
 * Start Haufen wiederherzustellen, deren Frist waehrend der Auszeit abgelaufen ist.
 *
 * <p>Der Test steht hier, weil man „das wird nirgends gespeichert" nur am Quelltext pruefen kann.
 */
class NoCoinPilePersistenceTest {

    private static final Path PLATFORM_CURRENCY =
            repositoryRoot().resolve("rpg-platform/src/main/java/rpg/platform/currency");

    @Test
    @DisplayName("kein Repository, keine Tabelle, kein Aggregattyp fuer einen Haufen")
    void noPileIsEverStored() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : sources()) {
            String text = Files.readString(source);
            for (String needle :
                    List.of(
                            "CoinPileRepository",
                            "AggregateType.COIN_PILE",
                            "INSERT INTO",
                            "markDirty")) {
                if (text.contains(needle)) {
                    offenders.add(source.getFileName() + " nennt " + needle);
                }
            }
        }

        assertThat(offenders)
                .as("ein Haufen ist eine Gelegenheit mit Frist, kein Besitz")
                .isEmpty();
    }

    @Test
    @DisplayName("es gibt keinen COIN_PILE-Aggregattyp - der Vorrat ist abgeschlossen")
    void thereIsNoPileAggregateType() {
        assertThat(rpg.core.persistence.AggregateType.values())
                .extracting(Enum::name)
                .doesNotContain("COIN_PILE", "COIN_DROP");
    }

    @Test
    @DisplayName("der Betrag steht im Datencontainer, nie in einem Inventar oder einer Tabelle")
    void theAmountLivesInTheDataContainer() throws IOException {
        String tag = Files.readString(PLATFORM_CURRENCY.resolve("CoinPileTag.java"));

        assertThat(tag)
                .as("Lore ist Darstellung, und Darstellung kann ein Client zum Luegen bringen")
                .contains("PersistentDataContainer")
                .doesNotContain("setLore(");
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(PLATFORM_CURRENCY)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

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
