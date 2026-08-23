package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T102 - der Preis steht bei dem, der ihn verlangt (SC-021, FR-050c, ADR-027).
 *
 * <p>Ein zentraler Preiskatalog ist die Abkuerzung, die sich anbietet und die ADR-027 abgelehnt hat:
 * wer den Reisepreis aendern will, soll die Zone bearbeiten, in der der Kristall steht, und nicht
 * eine Liste, in der zwanzig unverwandte Zahlen nebeneinander liegen.
 */
class PriceLivesWithTheZoneTest {

    @Test
    @DisplayName("jeder ausgelieferte Kristall traegt seinen Preis selbst")
    void everyShippedCrystalCarriesItsOwnPrice() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());

        assertThat(zones.crystals()).hasSize(6);
        assertThat(zones.crystals())
                .allSatisfy(
                        placement ->
                                assertThat(placement.crystal().price())
                                        .as(placement.crystal().key())
                                        .isNotNegative());
    }

    @Test
    @DisplayName("currency.yml kennt keinen Reisepreis")
    void theCurrencyFileKnowsNothingAboutTravel() throws IOException {
        String text =
                Files.readString(
                        repositoryRoot().resolve("rpg-plugin/src/main/resources/currency.yml"));

        assertThat(text.toLowerCase(java.util.Locale.ROOT))
                .as("waere der Preis hier, muesste man ihn an zwei Stellen suchen")
                .doesNotContain("waypoint")
                .doesNotContain("travel")
                .doesNotContain("crystal");
    }

    @Test
    @DisplayName("es gibt keinen zentralen Preiskatalog (ADR-027)")
    void thereIsNoCentralPriceCatalogue() throws IOException {
        Path resources = repositoryRoot().resolve("rpg-plugin/src/main/resources");

        try (var files = Files.list(resources)) {
            List<Path> catalogues =
                    files.filter(path -> path.getFileName().toString().endsWith(".yml"))
                            .filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .matches("(?i).*(price|cost|catalog).*"))
                            .toList();

            assertThat(catalogues).isEmpty();
        }
    }

    private static Path repositoryRoot() {
        Path at = Path.of("").toAbsolutePath();
        while (at != null && !Files.exists(at.resolve("settings.gradle.kts"))) {
            at = at.getParent();
        }
        if (at == null) {
            throw new IllegalStateException("repository root not found");
        }
        return at;
    }
}
